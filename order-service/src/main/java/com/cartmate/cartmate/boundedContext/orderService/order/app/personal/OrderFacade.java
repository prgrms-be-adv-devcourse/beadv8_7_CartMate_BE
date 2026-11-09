package com.cartmate.cartmate.boundedContext.orderService.order.app.personal;

import com.cartmate.cartmate.shared.coupon.dto.CouponOption;
import com.cartmate.cartmate.shared.order.OrderStatus;
import com.cartmate.cartmate.boundedContext.orderService.order.dto.personal.IndividualGroupOrderCreateRequest;
import com.cartmate.cartmate.boundedContext.orderService.order.dto.personal.OrderCreateRequest;
import com.cartmate.cartmate.boundedContext.orderService.order.dto.personal.OrderCreateResponse;
import com.cartmate.cartmate.boundedContext.orderService.order.dto.personal.OrderDetailResponse;
import com.cartmate.cartmate.boundedContext.orderService.order.dto.personal.OrderPaymentPreviewResponse;
import com.cartmate.cartmate.boundedContext.orderService.order.dto.personal.OrderSummaryResponse;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseClosedEvent;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseOpenedEvent;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseUpdatedEvent;
import com.cartmate.cartmate.shared.order.dto.OrderPaymentInfo;
import com.cartmate.cartmate.shared.payment.dto.PaymentResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.BuyerCancelResult;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderFacade {

    private final OrderCreateUseCase orderCreateUseCase;
    private final OrderQueryUseCase orderQueryUseCase;
    private final OrderPaymentUseCase orderPaymentUseCase;
    private final OrderCancelUseCase orderCancelUseCase;
    private final IndividualGroupPurchaseReplicaUseCase individualGroupPurchaseReplicaUseCase;
    private final OrderPurchaseConfirmationUseCase orderPurchaseConfirmationUseCase;
    private final Clock clock;

    @Transactional
    public OrderCreateResponse createPersonal(Long memberId, OrderCreateRequest request) {
        return orderCreateUseCase.createPersonal(memberId, request.cartItemIds(),
                request.deliveryAddress().toDeliveryAddress(), LocalDateTime.now(clock));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderCreateResponse createIndividualGroup(Long memberId, IndividualGroupOrderCreateRequest request) {
        return orderCreateUseCase.createIndividualGroup(memberId, request.individualGroupPurchaseId(), request.items(),
                request.deliveryAddress().toDeliveryAddress(), LocalDateTime.now(clock));
    }

    @Transactional(readOnly = true)
    public List<OrderSummaryResponse> getMyList(Long memberId) {
        return orderQueryUseCase.getMyList(memberId);
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse getDetail(Long memberId, Long orderId) {
        return orderQueryUseCase.getDetail(memberId, orderId);
    }

    @Transactional(readOnly = true)
    public OrderPaymentInfo getPaymentInfo(Long orderId, Long memberId, LocalDateTime now) {
        return orderPaymentUseCase.getPaymentInfo(orderId, memberId, now);
    }

    @Transactional
    public PaymentResult payOrder(Long memberId, Long orderId) {
        return payOrder(memberId, orderId, null);
    }

    @Transactional
    public PaymentResult payOrder(Long memberId, Long orderId, Long memberCouponId) {
        return orderPaymentUseCase.pay(orderId, memberId, memberCouponId, LocalDateTime.now(clock));
    }

    @Transactional(readOnly = true)
    public OrderPaymentPreviewResponse getPaymentPreview(Long memberId, Long orderId) {
        return getPaymentPreview(memberId, orderId, null);
    }

    @Transactional(readOnly = true)
    public OrderPaymentPreviewResponse getPaymentPreview(Long memberId, Long orderId, Long memberCouponId) {
        return orderPaymentUseCase.preview(orderId, memberId, memberCouponId, LocalDateTime.now(clock));
    }

    @Transactional(readOnly = true)
    public List<CouponOption> getCouponOptions(Long memberId, Long orderId) {
        return orderPaymentUseCase.couponOptions(orderId, memberId);
    }

    @Transactional
    public void applyPayment(Long orderId, Long memberId, Long paymentId, LocalDateTime paidAt) {
        orderPaymentUseCase.applyPayment(orderId, memberId, paymentId, paidAt);
    }

    @Transactional
    public BuyerCancelResult cancelByBuyer(Long memberId, Long orderId) {
        return orderCancelUseCase.cancelByBuyer(memberId, orderId);
    }

    @Transactional
    public void cancelByDeadline(Long orderId) {
        orderCancelUseCase.cancelByDeadline(orderId);
    }

    @Transactional
    public void cancelByOutOfStock(Long orderId) {
        orderCancelUseCase.cancelByOutOfStock(orderId);
    }

    @Transactional(readOnly = true)
    public List<Long> findIdsToCancelByDeadline(LocalDateTime now) {
        return orderCancelUseCase.findIdsToCancelByDeadline(now);
    }

    @Transactional(readOnly = true)
    public List<Long> findPendingIdsByProductIds(List<Long> productIds) {
        return orderCancelUseCase.findIdsByStatusAndProductIds(OrderStatus.PAYMENT_PENDING, productIds);
    }

    @Transactional(readOnly = true)
    public List<Long> findPaidIdsByProductIds(List<Long> productIds) {
        return orderCancelUseCase.findIdsByStatusAndProductIds(OrderStatus.PAID, productIds);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelBySalesStopped(Long orderId) {
        orderCancelUseCase.cancelBySalesStopped(orderId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelSellerItemsBySalesStopped(Long orderId, Long sellerId) {
        orderCancelUseCase.cancelSellerItemsBySalesStopped(orderId, sellerId);
    }

    @Transactional
    public void applyIndividualGroupPurchaseOpened(IndividualGroupPurchaseOpenedEvent event) {
        individualGroupPurchaseReplicaUseCase.applyOpened(event);
    }

    @Transactional
    public void applyIndividualGroupPurchaseUpdated(IndividualGroupPurchaseUpdatedEvent event) {
        individualGroupPurchaseReplicaUseCase.applyUpdated(event);
    }

    @Transactional
    public void applyIndividualGroupPurchaseClosed(IndividualGroupPurchaseClosedEvent event) {
        individualGroupPurchaseReplicaUseCase.applyClosed(event);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordPurchaseConfirmed(Long orderId, LocalDateTime confirmedAt) {
        orderPurchaseConfirmationUseCase.record(orderId, confirmedAt);
    }

    @Transactional(readOnly = true)
    public List<Long> findPendingIdsOfIndividualGroupPurchase(Long individualGroupPurchaseId) {
        return orderCancelUseCase.findPendingIdsOfIndividualGroupPurchase(individualGroupPurchaseId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelByGroupPurchaseClosed(Long orderId) {
        orderCancelUseCase.cancelByGroupPurchaseClosed(orderId);
    }

    @Transactional(readOnly = true)
    public long countPaymentPendingOfBuyer(Long buyerMemberId) {
        return orderQueryUseCase.countPaymentPendingOfBuyer(buyerMemberId);
    }
}
