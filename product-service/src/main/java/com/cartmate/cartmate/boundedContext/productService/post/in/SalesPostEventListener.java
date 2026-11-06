package com.cartmate.cartmate.boundedContext.productService.post.in;

import com.cartmate.cartmate.boundedContext.productService.post.app.SalesPostFacade;
import com.cartmate.cartmate.boundedContext.productService.post.dto.DeleteProductListRequest;
import com.cartmate.cartmate.boundedContext.productService.post.dto.DeleteProductRequest;
import com.cartmate.cartmate.boundedContext.productService.post.dto.SyncProductRequest;
import com.cartmate.cartmate.shared.member.event.SellerRegisteredEvent;
import com.cartmate.cartmate.boundedContext.productService.event.ProductCreatedEvent;
import com.cartmate.cartmate.boundedContext.productService.event.ProductDeletedEvent;
import com.cartmate.cartmate.boundedContext.productService.event.ProductListDeletedEvent;
import com.cartmate.cartmate.boundedContext.productService.event.ProductUpdatedEvent;
import com.cartmate.cartmate.shared.trust.event.SellerTrustScoreChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;
import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

@Component
@RequiredArgsConstructor
public class SalesPostEventListener {
    private final SalesPostFacade salesPostFacade;
    // product 이벤트에 담긴 product 쪽 dto를 post 쪽 dto로 바꾼다. Kafka로 바꾸면 JSON 역직렬화가 대신하므로 지운다
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(SellerRegisteredEvent event) {
        salesPostFacade.syncSeller(event);
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(ProductCreatedEvent event) {
        salesPostFacade.syncProduct(objectMapper.convertValue(event.getRequest(), SyncProductRequest.class));
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(ProductUpdatedEvent event) {
        salesPostFacade.syncProduct(objectMapper.convertValue(event.syncProductRequest(), SyncProductRequest.class));
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(ProductDeletedEvent event) {
        // product 쪽 dto를 post 쪽 dto로 바꿔 넘긴다(필드 하나라 생성자로). Kafka로 바꾸면 JSON 역직렬화가 대신하므로 지운다
        salesPostFacade.deleteProductByUser(new DeleteProductRequest(event.getRequest().getProductId()));
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(ProductListDeletedEvent event) {
        salesPostFacade.deleteProductByPost(objectMapper.convertValue(event.getRequest(), DeleteProductListRequest.class));
    }

    // 신뢰도 점수가 커밋된 뒤 판매글에 보여줄 판매자 신뢰도를 복제한다. 복제가 실패해도 점수 계산은 롤백되지 않는다.
    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(SellerTrustScoreChangedEvent event) {
        salesPostFacade.syncSellerTrust(event);
    }

}
