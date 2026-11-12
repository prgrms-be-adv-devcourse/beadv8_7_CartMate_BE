package com.cartmate.cartmate.boundedContext.productService.post.in;

import com.cartmate.cartmate.boundedContext.productService.post.app.SalesPostFacade;
import com.cartmate.cartmate.global.event.OutboxMessageRelayed;
import com.cartmate.cartmate.shared.member.event.SellerRegisteredEvent;
import com.cartmate.cartmate.shared.product.event.ProductDeletedEvent;
import com.cartmate.cartmate.shared.product.event.ProductsDeletedByPostEvent;
import com.cartmate.cartmate.shared.trust.event.SellerTrustScoreChangedEvent;
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

    // product가 보낸 삭제 이벤트. outbox 릴레이로 받는다
    // Kafka가 켜져 있으면 Kafka로 온 것만, 꺼져 있으면 같은 앱 안에서 온 것만 받는다(같은 이벤트를 두 번 처리하지 않게)
    @TransactionalEventListener(fallbackExecution = true, condition = "@relayedEventRouting.accepts(#message)")
    public void handle(OutboxMessageRelayed message) {
        // 판매글 삭제로 상품 삭제가 끝났을 때
        if (message.event() instanceof ProductsDeletedByPostEvent event) {
            salesPostFacade.completeDeleteByEvent(message.envelope().eventId(), event);
        }
        // 판매자가 상품 하나를 삭제했을 때
        if (message.event() instanceof ProductDeletedEvent event) {
            salesPostFacade.deleteProductByEvent(message.envelope().eventId(), event);
        }
    }

    // 신뢰도 점수가 커밋된 뒤 판매글에 보여줄 판매자 신뢰도를 복제한다. 복제가 실패해도 점수 계산은 롤백되지 않는다.
    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(SellerTrustScoreChangedEvent event) {
        salesPostFacade.syncSellerTrust(event);
    }

}
