package com.cartmate.cartmate.boundedContext.post.in;

import com.cartmate.cartmate.boundedContext.post.app.SalesPostFacade;
import com.cartmate.cartmate.shared.member.event.SellerRegisteredEvent;
import com.cartmate.cartmate.shared.product.event.ProductCreatedEvent;
import com.cartmate.cartmate.shared.product.event.ProductDeletedEvent;
import com.cartmate.cartmate.shared.product.event.ProductListDeletedEvent;
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
    public void handle(SellerRegisteredEvent event) {
        salesPostFacade.syncSeller(event);
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(ProductCreatedEvent event) {
        salesPostFacade.syncProduct(event.getRequest());
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(ProductDeletedEvent event) {
        salesPostFacade.deleteProductByUser(event.getRequest());
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(ProductListDeletedEvent event) {
        salesPostFacade.deleteProductByPost(event.getRequest());
    }
}
