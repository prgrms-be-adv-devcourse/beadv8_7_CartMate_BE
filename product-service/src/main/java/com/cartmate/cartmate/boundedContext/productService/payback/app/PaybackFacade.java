package com.cartmate.cartmate.boundedContext.productService.payback.app;

import com.cartmate.cartmate.boundedContext.productService.payback.domain.PaybackOrderItem;
import com.cartmate.cartmate.boundedContext.productService.payback.domain.PaybackPayoutStatus;
import com.cartmate.cartmate.boundedContext.productService.payback.dto.AdminPaybackConditionRequest;
import com.cartmate.cartmate.boundedContext.productService.payback.dto.PaybackConditionResponse;
import com.cartmate.cartmate.boundedContext.productService.payback.dto.PaybackPayoutResponse;
import com.cartmate.cartmate.boundedContext.productService.payback.dto.SellerPaybackConditionRequest;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseClosedEvent;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseOpenedEvent;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseUpdatedEvent;
import com.cartmate.cartmate.shared.order.OrderStatus;
import com.cartmate.cartmate.shared.wallet.event.PaybackDepositFailedEvent;
import com.cartmate.cartmate.shared.wallet.event.PaybackDepositedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaybackFacade {

    private final PaybackConditionUseCase paybackConditionUseCase;
    private final GroupPurchaseSnapshotUseCase groupPurchaseSnapshotUseCase;
    private final PaybackOrderUseCase paybackOrderUseCase;
    private final PaybackJudgeUseCase paybackJudgeUseCase;
    private final PaybackPayoutUseCase paybackPayoutUseCase;

    @Transactional
    public PaybackConditionResponse setAdminCondition(Long productId, AdminPaybackConditionRequest request) {
        return paybackConditionUseCase.setAdmin(productId, request);
    }

    @Transactional
    public void releaseAdminCondition(Long productId) {
        paybackConditionUseCase.releaseAdmin(productId);
    }

    @Transactional
    public PaybackConditionResponse setSellerCondition(Long sellerId, Long productId,
                                                       SellerPaybackConditionRequest request) {
        return paybackConditionUseCase.setSeller(sellerId, productId, request);
    }

    @Transactional
    public void releaseSellerCondition(Long sellerId, Long productId) {
        paybackConditionUseCase.releaseSeller(sellerId, productId);
    }

    @Transactional(readOnly = true)
    public List<PaybackConditionResponse> getConditions(Long productId) {
        return paybackConditionUseCase.findAll(productId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void applyGroupPurchaseOpened(IndividualGroupPurchaseOpenedEvent event) {
        paybackJudgeUseCase.judgeLocked(groupPurchaseSnapshotUseCase.applyOpened(event), LocalDateTime.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void applyGroupPurchaseUpdated(IndividualGroupPurchaseUpdatedEvent event) {
        paybackJudgeUseCase.judgeLocked(groupPurchaseSnapshotUseCase.applyUpdated(event), LocalDateTime.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void applyGroupPurchaseClosed(IndividualGroupPurchaseClosedEvent event) {
        paybackJudgeUseCase.judgeLocked(groupPurchaseSnapshotUseCase.applyClosed(event), LocalDateTime.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void applyOrderPlaced(Long orderId, Long individualGroupPurchaseId, Long buyerMemberId,
                                 List<PaybackOrderItem> items, OrderStatus status) {
        paybackOrderUseCase.applyPlaced(orderId, individualGroupPurchaseId, buyerMemberId, items, status,
                LocalDateTime.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void applyOrderCanceled(Long orderId) {
        paybackOrderUseCase.applyCanceled(orderId, LocalDateTime.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void applyOrderSellerItemsCanceled(Long orderId, Long sellerId) {
        paybackOrderUseCase.applySellerItemsCanceled(orderId, sellerId, LocalDateTime.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void applyPurchaseConfirmed(Long orderId, Long sellerId) {
        paybackOrderUseCase.applyConfirmed(orderId, sellerId, LocalDateTime.now());
    }

    @Transactional
    public void applyPaybackDeposited(PaybackDepositedEvent event) {
        paybackPayoutUseCase.applyDeposited(event);
    }

    @Transactional
    public void applyPaybackDepositFailed(PaybackDepositFailedEvent event) {
        paybackPayoutUseCase.applyDepositFailed(event);
    }

    @Transactional(readOnly = true)
    public List<Long> findRetryWaitingPaybackIds(int limit) {
        return paybackPayoutUseCase.findRetryWaitingIds(limit);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retryWaitingPayback(Long paybackId) {
        paybackPayoutUseCase.retryWaiting(paybackId, LocalDateTime.now());
    }

    @Transactional
    public void retryFailedPayback(Long paybackId) {
        paybackPayoutUseCase.retryFailed(paybackId, LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<PaybackPayoutResponse> getPaybackPayouts(PaybackPayoutStatus status) {
        return paybackPayoutUseCase.findAllByStatus(status);
    }
}
