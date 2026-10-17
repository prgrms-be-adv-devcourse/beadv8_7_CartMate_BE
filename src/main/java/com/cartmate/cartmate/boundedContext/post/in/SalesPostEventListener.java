package com.cartmate.cartmate.boundedContext.post.in;

import com.cartmate.cartmate.boundedContext.post.app.SalesPostFacade;
import com.cartmate.cartmate.shared.member.event.SellerCreatedEvent;
import com.cartmate.cartmate.shared.member.event.SellerUpdatedEvent;
import com.cartmate.cartmate.shared.product.event.ProductDeletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;
import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

@Component
@RequiredArgsConstructor
public class SalesPostEventListener {
    private final SalesPostFacade salesPostFacade;

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(SellerCreatedEvent event) {
        salesPostFacade.syncSeller(event.getSeller());
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(SellerUpdatedEvent event) {
        salesPostFacade.syncSeller(event.getSeller());
    }
}
