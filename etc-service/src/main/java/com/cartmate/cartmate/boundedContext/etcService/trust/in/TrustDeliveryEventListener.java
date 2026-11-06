package com.cartmate.cartmate.boundedContext.etcService.trust.in;

import com.cartmate.cartmate.boundedContext.etcService.trust.app.TrustFacade;
import com.cartmate.cartmate.global.event.OutboxMessageRelayed;
import com.cartmate.cartmate.shared.delivery.event.DeliveryClaimCompletedEvent;
import com.cartmate.cartmate.shared.delivery.event.DeliveryConfirmedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class TrustDeliveryEventListener {

    private final TrustFacade trustFacade;

    @TransactionalEventListener(fallbackExecution = true)
    public void handle(OutboxMessageRelayed message) {
        if (!(message.event() instanceof DeliveryConfirmedEvent event)) {
            return;
        }
        try {
            trustFacade.recordPurchaseConfirmed(event);
        } catch (Exception e) {
            log.error("구매확정 신뢰도 이력 저장 실패: deliveryId={}", event.deliveryId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(DeliveryClaimCompletedEvent event) {
        trustFacade.recordClaimCompleted(event);
    }
}
