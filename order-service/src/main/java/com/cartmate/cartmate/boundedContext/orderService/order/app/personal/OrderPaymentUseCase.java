package com.cartmate.cartmate.boundedContext.orderService.order.app.personal;

import com.cartmate.cartmate.shared.coupon.dto.CouponQuote;
import com.cartmate.cartmate.shared.coupon.dto.CouponOption;
import com.cartmate.cartmate.boundedContext.orderService.order.out.OrderCouponClient;
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
    private final OrderCouponClient orderCouponClient;

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
        applyPayment(orderId, memberId, paymentId, paidAt, null, null);
    }

    // memberCouponId: 결제에 쓴 쿠폰 (없으면 null). paidAmount: 실제로 빠진 금액 (모르면 null — 확인 생략)
    void applyPayment(Long orderId, Long memberId, Long paymentId, LocalDateTime paidAt,
                      Long memberCouponId, Long paidAmount) {
        Order order = orderSupport.getOrderForUpdate(orderId);
        orderSupport.validateBuyer(order, memberId);
        if (order.isPaymentPending()) {
            validatePurchasable(order.getItems().stream().map(OrderItem::getInventoryId).toList());
            if (!groupPurchaseAcceptsPayment(order, paidAt, true)) {
                throw new ServiceException(OrderErrorCode.ORDER_GROUP_PURCHASE_PAYMENT_CLOSED);
            }
        }
        // 쿠폰: 결제 반영과 같은 트랜잭션에서 사용 처리하고 할인을 기록한다 (반영이 롤백되면 쿠폰도 롤백)
        if (memberCouponId != null && order.isPaymentPending()) {
            CouponQuote quote = orderCouponClient.use(memberId, memberCouponId, orderId, order.couponTarget());
            order.applyCoupon(quote.memberCouponId(), quote.discountAmount(), quote.platformBurden(), quote.sellerId());
        }
        // 빠진 금액과 주문 금액(할인 반영)이 다르면 반영하지 않는다 → 결제를 되돌린다 (재시도 때 다른 쿠폰을 고른 경우 등)
        if (paidAmount != null && order.isPaymentPending() && order.payAmount() != paidAmount) {
            throw new ServiceException(OrderErrorCode.ORDER_PAYMENT_AMOUNT_MISMATCH);
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

    private void validatePurchasable(List<Long> inventoryIds) {
        List<OrderProductInfo> infos = orderProductClient.findAllByInventoryIds(inventoryIds);
        boolean purchasable = infos.size() == inventoryIds.stream().distinct().count()
                && infos.stream().allMatch(OrderProductInfo::purchasable);
        if (!purchasable) {
            throw new ServiceException(OrderErrorCode.ORDER_PRODUCT_NOT_PURCHASABLE);
        }
    }

    PaymentResult pay(Long orderId, Long memberId, LocalDateTime now) {
        return pay(orderId, memberId, null, now);
    }

    // memberCouponId: 결제에 쓸 쿠폰 (없으면 null). 쿠폰은 결제할 때 정한다 → 결제 전 취소·기한 만료면 쿠폰이 묶이지 않는다
    PaymentResult pay(Long orderId, Long memberId, Long memberCouponId, LocalDateTime now) {
        var existing = orderPaymentClient.findPayment(PaymentOrderType.INDIVIDUAL_GROUP, orderId, memberId);
        if (existing.isPresent() && existing.get().isPaid()) {
            return applyOrCompensate(orderId, memberId, existing.get(), memberCouponId);
        }
        OrderPaymentInfo info = getPaymentInfo(orderId, memberId, now);
        if (!info.payable()) {
            throw new ServiceException(OrderErrorCode.ORDER_NOT_PAYABLE);
        }
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ServiceException(OrderErrorCode.ORDER_NOT_FOUND));
        validatePurchasable(order.getItems().stream().map(OrderItem::getInventoryId).toList());
        long discount = memberCouponId == null ? 0
                : orderCouponClient.quote(memberId, memberCouponId, orderId, order.couponTarget()).discountAmount();
        PaymentResult payment = orderPaymentClient.charge(
                PaymentOrderType.INDIVIDUAL_GROUP, orderId, memberId, info.amount() - discount);
        return applyOrCompensate(orderId, memberId, payment, memberCouponId);
    }

    private PaymentResult applyOrCompensate(Long orderId, Long memberId, PaymentResult payment, Long memberCouponId) {
        try {
            applyPayment(orderId, memberId, payment.paymentId(), payment.paidAt(), memberCouponId, payment.amount());
            return payment;
        } catch (InternalServerUnavailableException e) {
            throw e;
        } catch (ServiceException e) {
            orderPaymentClient.compensateCharge(PaymentOrderType.INDIVIDUAL_GROUP, orderId, memberId);
            throw e;
        }
    }

    // 결제 화면 쿠폰 목록 (구매자만)
    List<CouponOption> couponOptions(Long orderId, Long memberId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ServiceException(OrderErrorCode.ORDER_NOT_FOUND));
        orderSupport.validateBuyer(order, memberId);
        return orderCouponClient.options(memberId, orderId, order.couponTarget());
    }

    OrderPaymentPreviewResponse preview(Long orderId, Long memberId, LocalDateTime now) {
        return preview(orderId, memberId, null, now);
    }

    // memberCouponId를 주면 그 쿠폰의 할인을 반영한 결제 금액을 보여 준다 (쓸 수 없는 쿠폰이면 그 이유로 예외)
    OrderPaymentPreviewResponse preview(Long orderId, Long memberId, Long memberCouponId, LocalDateTime now) {
        OrderPaymentInfo info = getPaymentInfo(orderId, memberId, now);
        PaymentResult existing = orderPaymentClient
                .findPayment(PaymentOrderType.INDIVIDUAL_GROUP, orderId, memberId).orElse(null);
        long discount = 0;
        if (memberCouponId != null) {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new ServiceException(OrderErrorCode.ORDER_NOT_FOUND));
            discount = orderCouponClient.quote(memberId, memberCouponId, orderId, order.couponTarget()).discountAmount();
        }
        long amount = info.amount() - discount;
        long balance = orderPaymentClient.getWalletBalance(memberId);
        return new OrderPaymentPreviewResponse(orderId, amount, info.payable(), balance,
                balance - amount, balance >= amount, existing, info.amount(), discount, memberCouponId);
    }
}
