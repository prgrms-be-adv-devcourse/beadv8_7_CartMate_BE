package com.cartmate.cartmate.boundedContext.orderService.order.in.salesstop;

import com.cartmate.cartmate.boundedContext.orderService.order.app.salesstop.SalesStopCancelFacade;
import com.cartmate.cartmate.shared.delivery.event.DeliverySalesStopCancelResultEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class SalesStopCancelResultListener {

    private final SalesStopCancelFacade salesStopCancelFacade;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void handle(DeliverySalesStopCancelResultEvent event) {
        salesStopCancelFacade.applyResult(event);
    }
}
