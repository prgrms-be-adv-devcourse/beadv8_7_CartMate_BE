package com.cartmate.cartmate.boundedContext.orderService.order.in.personal;

import com.cartmate.cartmate.boundedContext.orderService.order.app.personal.OrderFacade;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseClosedEvent;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseOpenedEvent;
import com.cartmate.cartmate.shared.cart.event.IndividualGroupPurchaseUpdatedEvent;
import com.cartmate.cartmate.global.event.OutboxMessageRelayed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class IndividualGroupPurchaseReplicaListener {

    private final OrderFacade orderFacade;

    @TransactionalEventListener(fallbackExecution = true, condition = "@relayedEventRouting.accepts(#message)")
    public void handle(OutboxMessageRelayed message) {
        switch (message.event()) {
            case IndividualGroupPurchaseOpenedEvent event -> orderFacade.applyIndividualGroupPurchaseOpened(event);
            case IndividualGroupPurchaseUpdatedEvent event -> orderFacade.applyIndividualGroupPurchaseUpdated(event);
            case IndividualGroupPurchaseClosedEvent event -> {
                orderFacade.applyIndividualGroupPurchaseClosed(event);
                cancelPendingOrders(event.individualGroupPurchaseId());
                orderFacade.judgeIndividualGroupPurchasePayback(event.individualGroupPurchaseId());
            }
            default -> {
            }
        }
    }

    private void cancelPendingOrders(Long individualGroupPurchaseId) {
        RuntimeException failure = null;
        for (Long orderId : orderFacade.findPendingIdsOfIndividualGroupPurchase(individualGroupPurchaseId)) {
            try {
                orderFacade.cancelByGroupPurchaseClosed(orderId);
            } catch (RuntimeException e) {
                log.warn("공동구매 종료 자동 취소 실패, 릴레이가 다시 시도: purchaseId={} orderId={}",
                        individualGroupPurchaseId, orderId, e);
                if (failure == null) {
                    failure = e;
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
