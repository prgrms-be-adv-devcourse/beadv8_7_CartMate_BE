package com.cartmate.cartmate.boundedContext.orderService.cart.in.groupindividual;

import com.cartmate.cartmate.shared.order.OrderType;

import com.cartmate.cartmate.boundedContext.orderService.cart.app.groupindividual.IndividualGroupPurchaseFacade;
import com.cartmate.cartmate.global.event.OutboxMessageRelayed;
import com.cartmate.cartmate.shared.order.event.OrderCanceledEvent;
import com.cartmate.cartmate.shared.order.event.OrderCreatedEvent;
import com.cartmate.cartmate.shared.order.event.OrderPaidEvent;
import com.cartmate.cartmate.shared.order.event.OrderSellerItemsCanceledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class IndividualGroupPurchaseOrderReplicaListener {

    private final IndividualGroupPurchaseFacade individualGroupPurchaseFacade;

    @TransactionalEventListener(fallbackExecution = true, condition = "@relayedEventRouting.accepts(#message)")
    public void handle(OutboxMessageRelayed message) {
        switch (message.event()) {
            case OrderCreatedEvent event when event.orderType() == OrderType.INDIVIDUAL_GROUP ->
                    individualGroupPurchaseFacade.applyOrderCreated(event);
            case OrderPaidEvent event when event.orderType() == OrderType.INDIVIDUAL_GROUP ->
                    individualGroupPurchaseFacade.applyOrderPaid(event);
            case OrderCanceledEvent event when event.orderType() == OrderType.INDIVIDUAL_GROUP ->
                    individualGroupPurchaseFacade.applyOrderCanceled(event);
            case OrderSellerItemsCanceledEvent event -> individualGroupPurchaseFacade.applyOrderSellerItemsCanceled(event);
            default -> {
            }
        }
    }
}
