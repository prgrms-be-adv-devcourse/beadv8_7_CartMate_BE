package com.cartmate.cartmate.boundedContext.order.app.personal;

import com.cartmate.cartmate.boundedContext.order.domain.DeliveryAddress;
import com.cartmate.cartmate.boundedContext.order.domain.personal.Order;
import com.cartmate.cartmate.boundedContext.order.domain.personal.OrderItem;
import com.cartmate.cartmate.boundedContext.order.dto.personal.OrderPaymentPreviewResponse;
import com.cartmate.cartmate.boundedContext.order.exception.OrderErrorCode;
import com.cartmate.cartmate.boundedContext.order.out.OrderPaymentClient;
import com.cartmate.cartmate.boundedContext.order.out.OrderProductClient;
import com.cartmate.cartmate.boundedContext.order.out.OrderProductInfo;
import com.cartmate.cartmate.boundedContext.order.out.personal.OrderRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.order.dto.DeliveryAddressInfo;
import com.cartmate.cartmate.shared.order.dto.OrderPaymentInfo;
import com.cartmate.cartmate.shared.order.event.OrderPaidEvent;
import com.cartmate.cartmate.shared.payment.dto.PaymentOrderType;
import com.cartmate.cartmate.shared.payment.dto.PaymentResult;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
class OrderPaymentUseCase {

    private final OrderRepository orderRepository;
    private final OrderSupport orderSupport;
    private final ApplicationEventPublisher eventPublisher;
    private final OrderProductClient orderProductClient;
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
                order.canAcceptPayment(now),
                order.getPaymentDeadlineAt()
        );
    }

    void applyPayment(Long orderId, Long memberId, Long paymentId, LocalDateTime paidAt) {
        Order order = orderSupport.getOrderForUpdate(orderId);
        orderSupport.validateBuyer(order, memberId);
        validatePurchasable(order.getItems().stream().map(OrderItem::getInventoryId).toList());
        if (order.markPaid(paymentId, paidAt)) {
            eventPublisher.publishEvent(toPaidEvent(order));
        }
    }

    private OrderPaidEvent toPaidEvent(Order order) {
        DeliveryAddress address = order.getDeliveryAddress();
        return new OrderPaidEvent(
                order.getId(),
                order.getOrderNumber(),
                order.getBuyerMemberId(),
                order.getOrderType().name(),
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
    // 지금은 한 트랜잭션이라 ④가 실패하면 ③도 롤백된다.
    // 결제 서버를 나누면 ③과 ④가 다른 트랜잭션이 되므로, ④ 실패 시 보상 환불을 추가한다.
    // ③ 동안 주문을 잠그지 않는 것도 분리 후를 대비한 순서다 (네트워크 대기 중 잠금을 잡지 않음).
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
    // 같은 앱(local)에선 한 트랜잭션이라 롤백으로 충분하고, 서버가 나뉘면(http) 결제 서버에 되돌리기를 요청한다.
    // 잠금 대기 같은 일시적 오류는 되돌리지 않고 그대로 던진다 → 재시도하면 위의 '이미 빠졌다' 경로로 반영된다.
    private PaymentResult applyOrCompensate(Long orderId, Long memberId, PaymentResult payment) {
        try {
            applyPayment(orderId, memberId, payment.paymentId(), payment.paidAt());
            return payment;
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
