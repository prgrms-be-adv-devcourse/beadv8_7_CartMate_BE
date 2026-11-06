package com.cartmate.cartmate.boundedContext.orderService.cart.in.personal;

import com.cartmate.cartmate.boundedContext.orderService.cart.app.personal.CartFacade;
import com.cartmate.cartmate.global.event.OutboxMessageRelayed;
import com.cartmate.cartmate.shared.order.event.OrderPaidEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class CartOrderEventListener {

    private final CartFacade cartFacade;

    @TransactionalEventListener(fallbackExecution = true)
    public void handle(OutboxMessageRelayed message) {
        if (message.event() instanceof OrderPaidEvent event) {
            cartFacade.removeOrderedItems(message.envelope().eventId(), event);
        }
    }
}
