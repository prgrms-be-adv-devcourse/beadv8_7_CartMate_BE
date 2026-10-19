package com.cartmate.cartmate.boundedContext.order.app.groupshared;

import com.cartmate.cartmate.boundedContext.order.dto.groupshared.SharedGroupOrderDetailResponse;
import com.cartmate.cartmate.boundedContext.order.dto.groupshared.SharedGroupOrderSummaryResponse;
import com.cartmate.cartmate.shared.cart.event.SharedGroupPurchaseClosedEvent;
import com.cartmate.cartmate.shared.order.dto.SharedGroupOrderPaymentInfo;
import com.cartmate.cartmate.shared.order.dto.TeamDeliveryAddress;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
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
    public void applyPayment(Long sharedGroupOrderId, Long memberId, LocalDateTime paidAt,
                             TeamDeliveryAddress teamDeliveryAddress) {
        sharedGroupOrderPaymentUseCase.applyPayment(sharedGroupOrderId, memberId, paidAt, teamDeliveryAddress);
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

    @Transactional(readOnly = true)
    public List<SharedGroupOrderSummaryResponse> getMyList(Long memberId) {
        return sharedGroupOrderQueryUseCase.getMyList(memberId);
    }

    @Transactional(readOnly = true)
    public SharedGroupOrderDetailResponse getDetail(Long memberId, Long sharedGroupOrderId) {
        return sharedGroupOrderQueryUseCase.getDetail(memberId, sharedGroupOrderId);
    }
}
