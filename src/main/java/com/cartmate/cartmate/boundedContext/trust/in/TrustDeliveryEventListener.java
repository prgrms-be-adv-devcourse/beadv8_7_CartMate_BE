package com.cartmate.cartmate.boundedContext.trust.in;

import com.cartmate.cartmate.boundedContext.trust.app.TrustFacade;
import com.cartmate.cartmate.shared.delivery.event.DeliveryClaimCompletedEvent;
import com.cartmate.cartmate.shared.delivery.event.DeliveryConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class TrustDeliveryEventListener {

    private final TrustFacade trustFacade;

    // 구매확정이 커밋된 뒤 이력을 남긴다. 이력 저장이 실패해도 구매확정과 정산은 롤백되지 않는다.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(DeliveryConfirmedEvent event) {
        trustFacade.recordPurchaseConfirmed(event);
    }

    // 클레임 처리 완료가 커밋된 뒤 이력을 남긴다. 이력 저장이 실패해도 클레임 완료는 롤백되지 않는다.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(DeliveryClaimCompletedEvent event) {
        trustFacade.recordClaimCompleted(event);
    }
}
