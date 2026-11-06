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
    private final IndividualGroupPurchasePaybackUseCase individualGroupPurchasePaybackUseCase;
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

    // 쿠폰 사용은 이 트랜잭션에 함께 들어간다 (결제 반영이 롤백되면 쿠폰 사용도 롤백)
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
    public void cancelByBuyer(Long memberId, Long orderId) {
        orderCancelUseCase.cancelByBuyer(memberId, orderId);
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

    // ===== 판매글 판매 중지 =====
    @Transactional(readOnly = true)
    public List<Long> findPendingIdsByProductIds(List<Long> productIds) {
        return orderCancelUseCase.findIdsByStatusAndProductIds(OrderStatus.PAYMENT_PENDING, productIds);
    }

    @Transactional(readOnly = true)
    public List<Long> findPaidIdsByProductIds(List<Long> productIds) {
        return orderCancelUseCase.findIdsByStatusAndProductIds(OrderStatus.PAID, productIds);
    }

    // 주문 하나마다 새 트랜잭션 (하나가 실패·거절돼도 다른 주문 처리에 영향 없음)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelBySalesStopped(Long orderId) {
        orderCancelUseCase.cancelBySalesStopped(orderId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelSellerItemsBySalesStopped(Long orderId, Long sellerId) {
        orderCancelUseCase.cancelSellerItemsBySalesStopped(orderId, sellerId);
    }

    // ===== 개인물품 공동구매 사본 (cart 이벤트 consumer가 부른다) =====
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

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void recordPurchaseConfirmed(Long orderId, LocalDateTime confirmedAt) {
        individualGroupPurchasePaybackUseCase.recordConfirmed(orderId, confirmedAt, LocalDateTime.now(clock));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void judgeIndividualGroupPurchasePayback(Long individualGroupPurchaseId) {
        individualGroupPurchasePaybackUseCase.judge(individualGroupPurchaseId, LocalDateTime.now(clock));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void judgeIndividualGroupPurchasePaybackOfOrder(Long orderId) {
        individualGroupPurchasePaybackUseCase.judgeOfOrder(orderId, LocalDateTime.now(clock));
    }

    // 공동구매가 닫힌 뒤 남은 결제 대기 주문 (사본을 닫는 트랜잭션이 커밋된 뒤 부른다)
    @Transactional(readOnly = true)
    public List<Long> findPendingIdsOfIndividualGroupPurchase(Long individualGroupPurchaseId) {
        return orderCancelUseCase.findPendingIdsOfIndividualGroupPurchase(individualGroupPurchaseId);
    }

    // 주문 하나마다 새 트랜잭션 (하나가 실패해도 이미 취소한 주문은 남고, 릴레이 재시도가 나머지를 이어 간다)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelByGroupPurchaseClosed(Long orderId) {
        orderCancelUseCase.cancelByGroupPurchaseClosed(orderId);
    }

    // 회원 탈퇴 확인: 결제 대기 중인 개인 주문 수
    @Transactional(readOnly = true)
    public long countPaymentPendingOfBuyer(Long buyerMemberId) {
        return orderQueryUseCase.countPaymentPendingOfBuyer(buyerMemberId);
    }
}
