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

    @Transactional(readOnly = true)
    public List<Long> findActiveIdsByProductIds(List<Long> productIds) {
        return sharedGroupOrderCancelUseCase.findActiveIdsByProductIds(productIds);
    }

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

    @Transactional(readOnly = true)
    public List<Long> findPayingIdsOfParticipant(Long memberId) {
        return sharedGroupOrderQueryUseCase.findIdsOfParticipant(memberId, List.of(SharedGroupOrderStatus.PAYING));
    }

    @Transactional(readOnly = true)
    public List<Long> findConfirmedIdsOfParticipant(Long memberId) {
        return sharedGroupOrderQueryUseCase.findIdsOfParticipant(memberId, List.of(SharedGroupOrderStatus.CONFIRMED));
    }
}
