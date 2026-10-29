package com.cartmate.cartmate.boundedContext.cart.in;

import com.cartmate.cartmate.boundedContext.cart.app.GroupPurchaseSalesStopUseCase;
import com.cartmate.cartmate.shared.post.event.SalesPostHiddenEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class CartPostEventListener {

    private final GroupPurchaseSalesStopUseCase salesStopUseCase;

    // 판매글 숨김이 커밋된 뒤 별도 스레드에서, 모집 중 공동구매를 하나씩 정리한다.
    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(SalesPostHiddenEvent event) {
        if (event.productIds() == null || event.productIds().isEmpty()) {
            return;
        }
        for (Long id : salesStopUseCase.findIndividualTargets(event.productIds())) {
            try {
                salesStopUseCase.removeFromIndividual(id, event.productIds());
            } catch (Exception e) {
                log.error("판매글 #{} 숨김: 개인물품 공동구매 #{} 정리 실패", event.postId(), id, e);
            }
        }
        for (Long id : salesStopUseCase.findSharedTargets(event.productIds())) {
            try {
                salesStopUseCase.removeFromShared(id, event.productIds());
            } catch (Exception e) {
                log.error("판매글 #{} 숨김: 공용물품 공동구매 #{} 정리 실패", event.postId(), id, e);
            }
        }
    }
}