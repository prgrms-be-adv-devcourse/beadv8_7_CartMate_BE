package com.cartmate.cartmate.boundedContext.productService.product.in;

import com.cartmate.cartmate.boundedContext.productService.product.app.ProductFacade;
import com.cartmate.cartmate.boundedContext.productService.event.productSyncCompletedEvent;
import com.cartmate.cartmate.boundedContext.productService.event.PostApiCallFailedEvent;
import com.cartmate.cartmate.boundedContext.productService.product.dto.ProductSyncUpdateRequest;
import com.cartmate.cartmate.boundedContext.productService.product.dto.RollbackProductRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;
import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

@Component
@RequiredArgsConstructor
public class ProductEventListener {
    private final ProductFacade productFacade;

    // 아래 두 핸들러는 post 이벤트에 담긴 post 쪽 dto를 product 쪽 dto로 바꿔 넘긴다. Kafka로 바꾸면 JSON 역직렬화가 대신하므로 지운다
    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(productSyncCompletedEvent event) {
        productFacade.updateSyncStatus(new ProductSyncUpdateRequest(event.getRequest().getSuccessList()));
    }

    @EventListener
    public void handle(PostApiCallFailedEvent event) {
        productFacade.rollbackByApiFail(new RollbackProductRequest(event.rollbackProductRequest().getPostId()));
    }
}
