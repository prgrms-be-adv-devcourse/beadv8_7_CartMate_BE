package com.cartmate.cartmate.boundedContext.order.app.groupshared;

import com.cartmate.cartmate.boundedContext.order.dto.groupshared.SharedGroupOrderDetailResponse;
import com.cartmate.cartmate.boundedContext.order.dto.groupshared.SharedGroupOrderSummaryResponse;
import com.cartmate.cartmate.shared.cart.event.SharedGroupPurchaseClosedEvent;
import com.cartmate.cartmate.shared.order.dto.SharedGroupOrderPaymentInfo;
import com.cartmate.cartmate.shared.order.dto.TeamDeliveryAddress;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SharedGroupOrderFacade {

    private final SharedGroupOrderCreateUseCase sharedGroupOrderCreateUseCase;
    private final SharedGroupOrderQueryUseCase sharedGroupOrderQueryUseCase;
    private final SharedGroupOrderPaymentUseCase sharedGroupOrderPaymentUseCase;
    private final SharedGroupOrderCancelUseCase sharedGroupOrderCancelUseCase;

    @Transactional
    public void createFromClosedPurchase(SharedGroupPurchaseClosedEvent event) {
        sharedGroupOrderCreateUseCase.createFromClosedPurchase(event);
    }

    @Transactional(readOnly = true)
    public SharedGroupOrderPaymentInfo getPaymentInfo(Long sharedGroupOrderId, Long memberId, LocalDateTime now) {
        return sharedGroupOrderPaymentUseCase.getPaymentInfo(sharedGroupOrderId, memberId, now);
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
}
