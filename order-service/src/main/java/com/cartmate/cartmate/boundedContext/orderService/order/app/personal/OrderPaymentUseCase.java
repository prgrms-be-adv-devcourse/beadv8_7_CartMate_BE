package com.cartmate.cartmate.boundedContext.orderService.order.app.personal;

import com.cartmate.cartmate.global.http.InternalApiClient.InternalServerUnavailableException;

import com.cartmate.cartmate.boundedContext.orderService.order.domain.DeliveryAddress;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.Order;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.OrderItem;
import com.cartmate.cartmate.boundedContext.orderService.order.dto.personal.OrderPaymentPreviewResponse;
import com.cartmate.cartmate.boundedContext.orderService.order.exception.OrderErrorCode;
import com.cartmate.cartmate.boundedContext.orderService.order.out.OrderPaymentClient;
import com.cartmate.cartmate.boundedContext.orderService.order.out.OrderProductClient;
import com.cartmate.cartmate.boundedContext.orderService.order.out.OrderProductInfo;
import com.cartmate.cartmate.boundedContext.orderService.order.out.personal.OrderRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.order.dto.DeliveryAddressInfo;
import com.cartmate.cartmate.shared.order.dto.OrderPaymentInfo;
import com.cartmate.cartmate.shared.order.event.OrderPaidEvent;
import com.cartmate.cartmate.global.event.DomainEventPublisher;
import com.cartmate.cartmate.shared.payment.dto.PaymentOrderType;
import com.cartmate.cartmate.shared.payment.dto.PaymentResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
class OrderPaymentUseCase {

    private final OrderRepository orderRepository;
    private final OrderSupport orderSupport;
    private final DomainEventPublisher eventPublisher;
    private final OrderProductClient orderProductClient;
    private final IndividualGroupPurchaseReplicaUseCase individualGroupPurchaseReplicaUseCase;
    private final OrderPaymentClient orderPaymentClient;

    OrderPaymentInfo getPaymentInfo(Long orderId, Long memberId, LocalDateTime now) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ServiceException(OrderErrorCode.ORDER_NOT_FOUND));
        orderSupport.validateBuyer(order, memberId);
        return new OrderPaymentInfo(
                order.getId(),
                memberId,
                order.getOrderNumber(),
                order.getTotalAmount(),
                order.canAcceptPayment(now) && groupPurchaseAcceptsPayment(order, now, false),
                order.getPaymentDeadlineAt()
        );
    }

    void applyPayment(Long orderId, Long memberId, Long paymentId, LocalDateTime paidAt) {
        Order order = orderSupport.getOrderForUpdate(orderId);
        orderSupport.validateBuyer(order, memberId);
        // 같은 결제의 재통보(이미 PAID)는 markPaid가 멱등으로 넘기므로 결제 대기일 때만 다시 본다
        if (order.isPaymentPending()) {
            validatePurchasable(order.getItems().stream().map(OrderItem::getInventoryId).toList());
            if (!groupPurchaseAcceptsPayment(order, paidAt, true)) {
                throw new ServiceException(OrderErrorCode.ORDER_GROUP_PURCHASE_PAYMENT_CLOSED);
            }
        }
        if (order.markPaid(paymentId, paidAt)) {
            eventPublisher.publish(toPaidEvent(order));
        }
    }

    private OrderPaidEvent toPaidEvent(Order order) {
        DeliveryAddress address = order.getDeliveryAddress();
        return new OrderPaidEvent(
                order.getId(),
                order.getOrderNumber(),
                order.getBuyerMemberId(),
                order.getOrderType(),
                order.getIndividualGroupPurchaseId(),
                order.getPaidAt(),
                new DeliveryAddressInfo(
                        address.getReceiverName(),
                        address.getReceiverPhone(),
                        address.getZipCode(),
                        address.getAddress(),
                        address.getAddressDetail()
                ),
                order.getItems().stream()
                        .map(item -> new OrderPaidEvent.Item(
                                item.getId(),
                                item.getProductId(),
                                item.getInventoryId(),
                                item.getSellerId(),
                                item.getProductName(),
                                item.getOptionName(),
                                item.getQuantity(),
                                item.getUnitPrice()))
                        .toList()
        );
    }

    // 결제 마감 = 판매 마감(ARCH-17). 개인물품 주문은 order 쪽 공동구매 사본이 지금 열려 있고 주문 상품을 팔 때만 결제한다.
    // 사본이 없으면 cart에 잠그지 않고 묻는다. 공동구매를 못 찾으면 결제하지 않는다
    // 결제 반영(forUpdate)은 사본을 공유 락으로 다시 읽어 결제 트랜잭션의 옛 스냅샷을 쓰지 않는다
    private boolean groupPurchaseAcceptsPayment(Order order, LocalDateTime now, boolean forPayment) {
        if (!order.isIndividualGroup()) {
            return true;
        }
        Long purchaseId = order.getIndividualGroupPurchaseId();
        return (forPayment ? individualGroupPurchaseReplicaUseCase.findForPayment(purchaseId)
                : individualGroupPurchaseReplicaUseCase.find(purchaseId))
                .map(purchase -> purchase.canAcceptPayment(order.productIds(), now))
                .orElse(false);
    }

    // 판매글 판매 중지 등으로 주문 상품 하나라도 판매 중이 아니면 결제 전체를 거절한다 (지갑 차감까지 롤백).
    // 결제 정보 조회와 결제 반영 사이에 판매글이 내려가는 경우를 주문을 잠근 상태에서 막는다.
    private void validatePurchasable(List<Long> inventoryIds) {
        List<OrderProductInfo> infos = orderProductClient.findAllByInventoryIds(inventoryIds);
        boolean purchasable = infos.size() == inventoryIds.stream().distinct().count()
                && infos.stream().allMatch(OrderProductInfo::purchasable);
        if (!purchasable) {
            throw new ServiceException(OrderErrorCode.ORDER_PRODUCT_NOT_PURCHASABLE);
        }
    }

    // ===== 결제 (주문이 지휘) =====
    // ① 이미 결제했는지 → ② 결제 가능 확인 (잠금 없이) → ③ 결제에 차감 요청 → ④ 잠그고 PAID 반영
    PaymentResult pay(Long orderId, Long memberId, LocalDateTime now) {
        var existing = orderPaymentClient.findPayment(PaymentOrderType.INDIVIDUAL_GROUP, orderId, memberId);
        if (existing.isPresent() && existing.get().isPaid()) {
            // 이미 빠졌다 → 주문 반영만 다시 시도 (이미 반영됐으면 아무것도 안 함). 재시도가 결국 같은 결과로 모인다.
            return applyOrCompensate(orderId, memberId, existing.get());
        }
        OrderPaymentInfo info = getPaymentInfo(orderId, memberId, now); // 구매자가 아니면 예외
        if (!info.payable()) {
            throw new ServiceException(OrderErrorCode.ORDER_NOT_PAYABLE);
        }
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ServiceException(OrderErrorCode.ORDER_NOT_FOUND));
        validatePurchasable(order.getItems().stream().map(OrderItem::getInventoryId).toList()); // 차감 전에 먼저 확인
        PaymentResult payment = orderPaymentClient.charge(
                PaymentOrderType.INDIVIDUAL_GROUP, orderId, memberId, info.amount());
        // 이미 빠졌다 → 주문 반영만 다시 시도 (이미 반영됐으면 아무것도 안 함). 재시도가 결국 같은 결과로 모인다.
        return applyOrCompensate(orderId, memberId, payment);
    }

    // 주문 반영이 거절되면(그 사이 기한 만료·취소, 판매 중지 등) 빠진 돈을 되돌린다.
    private PaymentResult applyOrCompensate(Long orderId, Long memberId, PaymentResult payment) {
        try {
            applyPayment(orderId, memberId, payment.paymentId(), payment.paidAt());
            return payment;
        } catch (InternalServerUnavailableException e) {
            throw e;
        } catch (ServiceException e) {
            orderPaymentClient.compensateCharge(PaymentOrderType.INDIVIDUAL_GROUP, orderId, memberId);
            throw e;
        }
    }
    OrderPaymentPreviewResponse preview(Long orderId, Long memberId, LocalDateTime now) {
        OrderPaymentInfo info = getPaymentInfo(orderId, memberId, now);
        PaymentResult existing = orderPaymentClient
                .findPayment(PaymentOrderType.INDIVIDUAL_GROUP, orderId, memberId).orElse(null);
        long balance = orderPaymentClient.getWalletBalance(memberId);
        return new OrderPaymentPreviewResponse(orderId, info.amount(), info.payable(), balance,
                balance - info.amount(), balance >= info.amount(), existing);
    }
}
