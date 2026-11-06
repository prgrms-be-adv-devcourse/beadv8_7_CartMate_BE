package com.cartmate.cartmate.boundedContext.orderService.order.app.groupshared;

import com.cartmate.cartmate.shared.order.SharedGroupOrderStatus;
import com.cartmate.cartmate.boundedContext.orderService.order.dto.groupshared.SharedGroupOrderDetailResponse;
import com.cartmate.cartmate.boundedContext.orderService.order.dto.groupshared.SharedGroupOrderSummaryResponse;
import com.cartmate.cartmate.shared.cart.event.SharedGroupPurchaseClosedEvent;
import com.cartmate.cartmate.shared.order.dto.SharedGroupOrderPaymentInfo;
import com.cartmate.cartmate.shared.order.dto.TeamDeliveryAddress;
import com.cartmate.cartmate.shared.payment.dto.PaymentResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SharedGroupOrderFacade {

    private final SharedGroupOrderCreateUseCase sharedGroupOrderCreateUseCase;
    private final SharedGroupOrderQueryUseCase sharedGroupOrderQueryUseCase;
    private final SharedGroupOrderPaymentUseCase sharedGroupOrderPaymentUseCase;
    private final SharedGroupOrderCancelUseCase sharedGroupOrderCancelUseCase;
    private final Clock clock;

    // 아웃박스 consumer(SharedGroupOrderEventListener)가 부른다. 마감 트랜잭션과 따로, order 자기 트랜잭션에서 만든다
    @Transactional
    public void createFromClosedPurchase(SharedGroupPurchaseClosedEvent event) {
        sharedGroupOrderCreateUseCase.createFromClosedPurchase(event, LocalDateTime.now(clock));
    }

    @Transactional
    public void reportCreationFailed(Long sharedGroupPurchaseId, String reason) {
        sharedGroupOrderCreateUseCase.reportCreationFailed(sharedGroupPurchaseId, reason);
    }

    @Transactional(readOnly = true)
    public SharedGroupOrderPaymentInfo getPaymentInfo(Long sharedGroupOrderId, Long memberId, LocalDateTime now) {
        return sharedGroupOrderPaymentUseCase.getPaymentInfo(sharedGroupOrderId, memberId, now);
    }

    // 분담금 결제: 주문이 지휘한다 (주문 → 결제)
    @Transactional
    public PaymentResult pay(Long memberId, Long sharedGroupOrderId, TeamDeliveryAddress teamDeliveryAddress) {
        return sharedGroupOrderPaymentUseCase.pay(sharedGroupOrderId, memberId, teamDeliveryAddress, LocalDateTime.now(clock));
    }

    @Transactional
    public void markStockReserved(Long sharedGroupOrderId) {
        sharedGroupOrderPaymentUseCase.markStockReserved(sharedGroupOrderId);
    }

    @Transactional
    public void applyPayment(Long sharedGroupOrderId, Long memberId, Long paymentId, LocalDateTime paidAt,
                             TeamDeliveryAddress teamDeliveryAddress) {
        sharedGroupOrderPaymentUseCase.applyPayment(sharedGroupOrderId, memberId, paymentId, paidAt, teamDeliveryAddress);
    }

    @Transactional
    public void cancelByCreator(Long memberId, Long sharedGroupOrderId) {
        sharedGroupOrderCancelUseCase.cancelByCreator(memberId, sharedGroupOrderId);
    }

    @Transactional
    public void cancelByDeadline(Long sharedGroupOrderId) {
        sharedGroupOrderCancelUseCase.cancelByDeadline(sharedGroupOrderId);
    }

    @Transactional
    public void cancelByOutOfStock(Long sharedGroupOrderId) {
        sharedGroupOrderCancelUseCase.cancelByOutOfStock(sharedGroupOrderId);
    }

    @Transactional(readOnly = true)
    public List<Long> findIdsToCancelByDeadline(LocalDateTime now) {
        return sharedGroupOrderCancelUseCase.findIdsToCancelByDeadline(now);
    }

    // ===== 판매글 판매 중지 =====
    @Transactional(readOnly = true)
    public List<Long> findActiveIdsByProductIds(List<Long> productIds) {
        return sharedGroupOrderCancelUseCase.findActiveIdsByProductIds(productIds);
    }

    // 공동 주문 하나마다 새 트랜잭션. 발송된 배송이 있으면 delivery가 거절 → 이 트랜잭션만 롤백
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelBySalesStopped(Long sharedGroupOrderId) {
        sharedGroupOrderCancelUseCase.cancelBySalesStopped(sharedGroupOrderId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelSellerItemsBySalesStopped(Long sharedGroupOrderId, Long sellerId) {
            sharedGroupOrderCancelUseCase.cancelSellerItemsBySalesStopped(sharedGroupOrderId, sellerId);
    }

    @Transactional(readOnly = true)
    public List<SharedGroupOrderSummaryResponse> getMyList(Long memberId) {
        return sharedGroupOrderQueryUseCase.getMyList(memberId);
    }

    @Transactional(readOnly = true)
    public SharedGroupOrderDetailResponse getDetail(Long memberId, Long sharedGroupOrderId) {
        return sharedGroupOrderQueryUseCase.getDetail(memberId, sharedGroupOrderId);
    }

    // 회원 탈퇴 확인: 참여 중인 결제 중 공동 주문
    @Transactional(readOnly = true)
    public List<Long> findPayingIdsOfParticipant(Long memberId) {
        return sharedGroupOrderQueryUseCase.findIdsOfParticipant(memberId, List.of(SharedGroupOrderStatus.PAYING));
    }

    // 회원 탈퇴 확인: 참여 중인 확정 공동 주문 (배송이 구매확정까지 끝났는지는 delivery가 판단)
    @Transactional(readOnly = true)
    public List<Long> findConfirmedIdsOfParticipant(Long memberId) {
        return sharedGroupOrderQueryUseCase.findIdsOfParticipant(memberId, List.of(SharedGroupOrderStatus.CONFIRMED));
    }
}
