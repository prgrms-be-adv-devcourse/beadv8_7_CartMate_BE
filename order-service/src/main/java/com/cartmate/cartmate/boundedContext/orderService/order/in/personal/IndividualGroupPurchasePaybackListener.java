package com.cartmate.cartmate.boundedContext.orderService.order.in.personal;

import com.cartmate.cartmate.boundedContext.orderService.order.app.personal.OrderFacade;
import com.cartmate.cartmate.global.event.OutboxMessageRelayed;
import com.cartmate.cartmate.shared.delivery.PurchaseType;
import com.cartmate.cartmate.shared.delivery.event.DeliveryConfirmedEvent;
import com.cartmate.cartmate.shared.order.event.OrderCanceledEvent;
import com.cartmate.cartmate.shared.order.event.OrderSellerItemsCanceledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class IndividualGroupPurchasePaybackListener {

    private static final String INDIVIDUAL_GROUP = "INDIVIDUAL_GROUP";

    private final OrderFacade orderFacade;

    @TransactionalEventListener(fallbackExecution = true)
    public void handle(OutboxMessageRelayed message) {
        switch (message.event()) {
            case OrderCanceledEvent event when INDIVIDUAL_GROUP.equals(event.orderType()) ->
                    orderFacade.judgeIndividualGroupPurchasePaybackOfOrder(event.orderId());
            case OrderSellerItemsCanceledEvent event ->
                    orderFacade.judgeIndividualGroupPurchasePaybackOfOrder(event.orderId());
            default -> {
            }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(DeliveryConfirmedEvent event) {
        if (event.purchaseType() == PurchaseType.INDIVIDUAL_GROUP) {
            orderFacade.recordPurchaseConfirmed(event.orderId(), event.confirmedAt());
        }
    }
}
