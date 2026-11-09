package com.cartmate.cartmate.boundedContext.etcService.delivery.in;

import com.cartmate.cartmate.boundedContext.etcService.delivery.app.DeliveryFacade;
import com.cartmate.cartmate.shared.payment.event.ClaimRefundCompletedEvent;
import com.cartmate.cartmate.shared.payment.event.ClaimRefundFailedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeliveryPaymentEventListener {

    private final DeliveryFacade deliveryFacade;

    @EventListener
    public void handle(ClaimRefundCompletedEvent event) {
        deliveryFacade.completeClaimRefund(event.claimId());
    }

    @EventListener
    public void handle(ClaimRefundFailedEvent event) {
        deliveryFacade.failClaimRefund(event.claimId());
    }
}
