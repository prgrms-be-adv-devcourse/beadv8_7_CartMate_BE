package com.cartmate.cartmate.boundedContext.productService.product.in;

import com.cartmate.cartmate.boundedContext.productService.product.app.ProductFacade;
import com.cartmate.cartmate.global.event.OutboxMessageRelayed;
import com.cartmate.cartmate.shared.post.event.SalesPostDeletedEvent;
import com.cartmate.cartmate.shared.post.event.SalesPostRegisterFailedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;


@Component
@RequiredArgsConstructor
public class ProductEventListener {
    private final ProductFacade productFacade;

    // 판매글 삭제 · 등록 실패. outbox 릴레이로 받는다
    // Kafka가 켜져 있으면 Kafka로 온 것만, 꺼져 있으면 같은 앱 안에서 온 것만 받는다(같은 이벤트를 두 번 처리하지 않게)
    @TransactionalEventListener(fallbackExecution = true, condition = "@relayedEventRouting.accepts(#message)")
    public void handle(OutboxMessageRelayed message) {
        if (message.event() instanceof SalesPostDeletedEvent event) {
            productFacade.deleteProductByPost(message.envelope().eventId(), event.postId());
        } else if (message.event() instanceof SalesPostRegisterFailedEvent event) {
            productFacade.rollbackByApiFail(message.envelope().eventId(), event.postId());
        }
    }
}
