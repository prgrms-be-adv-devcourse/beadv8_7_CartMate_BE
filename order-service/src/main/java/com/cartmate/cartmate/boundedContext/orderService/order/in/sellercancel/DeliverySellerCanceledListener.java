package com.cartmate.cartmate.boundedContext.orderService.order.in.sellercancel;

import com.cartmate.cartmate.boundedContext.orderService.order.app.sellercancel.SellerCancelFacade;
import com.cartmate.cartmate.shared.delivery.event.DeliverySellerCanceledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class DeliverySellerCanceledListener {

    private final SellerCancelFacade sellerCancelFacade;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void handle(DeliverySellerCanceledEvent event) {
        sellerCancelFacade.apply(event);
    }
}
