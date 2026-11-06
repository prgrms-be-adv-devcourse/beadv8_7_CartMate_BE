package com.cartmate.cartmate.boundedContext.productService.payback.in;

import com.cartmate.cartmate.boundedContext.productService.payback.app.PaybackFacade;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseClosedEvent;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseOpenedEvent;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseUpdatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class GroupPurchasePaybackListener {

    private final PaybackFacade paybackFacade;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void handle(IndividualGroupPurchaseOpenedEvent event) {
        paybackFacade.applyGroupPurchaseOpened(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void handle(IndividualGroupPurchaseUpdatedEvent event) {
        paybackFacade.applyGroupPurchaseUpdated(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void handle(IndividualGroupPurchaseClosedEvent event) {
        paybackFacade.applyGroupPurchaseClosed(event);
    }
}
