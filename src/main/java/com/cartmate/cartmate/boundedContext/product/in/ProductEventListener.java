package com.cartmate.cartmate.boundedContext.product.in;

import com.cartmate.cartmate.boundedContext.product.app.ProductFacade;
import com.cartmate.cartmate.shared.post.dto.productSyncCompletedEvent;
import com.cartmate.cartmate.shared.product.event.ProductDeletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;
import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

@Component
@RequiredArgsConstructor
public class ProductEventListener {
    private final ProductFacade productFacade;

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(productSyncCompletedEvent event) {
        productFacade.updateSyncStatus(event.getRequest());
    }
}
