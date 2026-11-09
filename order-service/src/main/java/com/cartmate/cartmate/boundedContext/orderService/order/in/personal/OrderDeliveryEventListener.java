package com.cartmate.cartmate.boundedContext.orderService.order.in.personal;

import com.cartmate.cartmate.boundedContext.orderService.order.app.personal.OrderFacade;
import com.cartmate.cartmate.shared.delivery.event.DeliveryOrderCancelRejectedEvent;
import com.cartmate.cartmate.shared.delivery.event.DeliveryOrderCanceledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class OrderDeliveryEventListener {

    private final OrderFacade orderFacade;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void handle(DeliveryOrderCanceledEvent event) {
        orderFacade.confirmCancelByDelivery(event.orderId(), event.requestSeq());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void handle(DeliveryOrderCancelRejectedEvent event) {
        orderFacade.rejectCancelByDelivery(event.orderId(), event.requestSeq(), event.failureCode());
    }
}
