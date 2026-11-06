package com.cartmate.cartmate.boundedContext.productService.payback.app;

import com.cartmate.cartmate.boundedContext.productService.payback.dto.AdminPaybackConditionRequest;
import com.cartmate.cartmate.boundedContext.productService.payback.dto.PaybackConditionResponse;
import com.cartmate.cartmate.boundedContext.productService.payback.dto.SellerPaybackConditionRequest;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseClosedEvent;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseOpenedEvent;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseUpdatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PaybackFacade {

    private final PaybackConditionUseCase paybackConditionUseCase;
    private final GroupPurchaseSnapshotUseCase groupPurchaseSnapshotUseCase;

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
        groupPurchaseSnapshotUseCase.applyOpened(event);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void applyGroupPurchaseUpdated(IndividualGroupPurchaseUpdatedEvent event) {
        groupPurchaseSnapshotUseCase.applyUpdated(event);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void applyGroupPurchaseClosed(IndividualGroupPurchaseClosedEvent event) {
        groupPurchaseSnapshotUseCase.applyClosed(event);
    }
}
