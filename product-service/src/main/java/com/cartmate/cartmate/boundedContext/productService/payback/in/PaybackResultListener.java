package com.cartmate.cartmate.boundedContext.productService.payback.in;

import com.cartmate.cartmate.boundedContext.productService.payback.app.PaybackFacade;
import com.cartmate.cartmate.shared.wallet.event.PaybackDepositFailedEvent;
import com.cartmate.cartmate.shared.wallet.event.PaybackDepositedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class PaybackResultListener {

    private final PaybackFacade paybackFacade;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void handle(PaybackDepositedEvent event) {
        paybackFacade.applyPaybackDeposited(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void handle(PaybackDepositFailedEvent event) {
        paybackFacade.applyPaybackDepositFailed(event);
    }
}
