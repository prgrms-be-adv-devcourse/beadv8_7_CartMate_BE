package com.cartmate.cartmate.boundedContext.orderService.cart.in.matching;

import com.cartmate.cartmate.boundedContext.orderService.cart.app.matching.CartMatchingFacade;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseClosedEvent;
import com.cartmate.cartmate.shared.delivery.event.DeliveryConfirmedEvent;
import com.cartmate.cartmate.global.event.OutboxMessageRelayed;
import com.cartmate.cartmate.shared.order.event.OrderCanceledEvent;
import com.cartmate.cartmate.shared.order.event.OrderSellerItemsCanceledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class PaybackJudgmentListener {

    private static final String INDIVIDUAL_GROUP = "INDIVIDUAL_GROUP";
    private static final String ADMIN_DEMAND = "ADMIN_DEMAND";

    private final CartMatchingFacade cartMatchingFacade;

    @TransactionalEventListener(fallbackExecution = true)
    public void handle(OutboxMessageRelayed message) {
        switch (message.event()) {
            case IndividualGroupPurchaseClosedEvent event when ADMIN_DEMAND.equals(event.origin()) ->
                    cartMatchingFacade.judgePayback(event.individualGroupPurchaseId());
            case OrderCanceledEvent event when INDIVIDUAL_GROUP.equals(event.orderType()) ->
                    cartMatchingFacade.recordPaybackOrderCanceled(event.orderId(), event.canceledAt());
            case OrderSellerItemsCanceledEvent event -> cartMatchingFacade.recordPaybackSellerItemsCanceled(
                    event.orderId(), event.sellerId(), event.canceledAt());
            default -> {
            }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(DeliveryConfirmedEvent event) {
        if (INDIVIDUAL_GROUP.equals(String.valueOf(event.purchaseType()))) {
            cartMatchingFacade.recordPaybackOrderConfirmed(event.orderId(), event.confirmedAt());
        }
    }
}
