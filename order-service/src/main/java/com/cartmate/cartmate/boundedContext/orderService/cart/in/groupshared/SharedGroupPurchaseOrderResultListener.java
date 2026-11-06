package com.cartmate.cartmate.boundedContext.orderService.cart.in.groupshared;

import com.cartmate.cartmate.boundedContext.orderService.cart.app.groupshared.SharedGroupPurchaseFacade;
import com.cartmate.cartmate.global.event.OutboxMessageRelayed;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCreatedEvent;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCreationFailedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class SharedGroupPurchaseOrderResultListener {

    private final SharedGroupPurchaseFacade sharedGroupPurchaseFacade;

    @TransactionalEventListener(fallbackExecution = true)
    public void handle(OutboxMessageRelayed message) {
        switch (message.event()) {
            case SharedGroupOrderCreatedEvent event -> sharedGroupPurchaseFacade.markOrderCreated(event);
            case SharedGroupOrderCreationFailedEvent event -> sharedGroupPurchaseFacade.revertByOrderCreationFailed(event);
            default -> {
            }
        }
    }
}
