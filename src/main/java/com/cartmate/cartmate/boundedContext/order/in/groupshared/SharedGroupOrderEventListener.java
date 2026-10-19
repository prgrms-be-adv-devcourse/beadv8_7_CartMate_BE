package com.cartmate.cartmate.boundedContext.order.in.groupshared;

import com.cartmate.cartmate.boundedContext.order.app.groupshared.SharedGroupOrderFacade;
import com.cartmate.cartmate.shared.cart.event.SharedGroupPurchaseClosedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SharedGroupOrderEventListener {

    private final SharedGroupOrderFacade sharedGroupOrderFacade;

    @EventListener
    public void handle(SharedGroupPurchaseClosedEvent event) {
        sharedGroupOrderFacade.createFromClosedPurchase(event);
    }
}
