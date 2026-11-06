package com.cartmate.cartmate.boundedContext.orderService.order.in.personal;

import com.cartmate.cartmate.boundedContext.orderService.order.app.personal.OrderFacade;
import com.cartmate.cartmate.shared.delivery.PurchaseType;
import com.cartmate.cartmate.shared.delivery.event.DeliveryConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class OrderPurchaseConfirmationListener {

    private final OrderFacade orderFacade;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(DeliveryConfirmedEvent event) {
        if (event.purchaseType() == PurchaseType.INDIVIDUAL_GROUP) {
            orderFacade.recordPurchaseConfirmed(event.orderId(), event.confirmedAt());
        }
    }
}
