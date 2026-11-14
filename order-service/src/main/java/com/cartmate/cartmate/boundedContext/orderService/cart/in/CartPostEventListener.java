package com.cartmate.cartmate.boundedContext.orderService.cart.in;

import com.cartmate.cartmate.boundedContext.orderService.cart.app.GroupPurchaseSalesStopUseCase;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.post.event.SalesPostHiddenEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CartPostEventListener {

    private final GroupPurchaseSalesStopUseCase salesStopUseCase;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(SalesPostHiddenEvent event) {
        if (event.productIds() == null || event.productIds().isEmpty()) {
            return;
        }
        List<RuntimeException> failures = new ArrayList<>();
        for (Long id : salesStopUseCase.findIndividualTargets(event.productIds())) {
            run(event, "개인물품 공동구매", id, () -> salesStopUseCase.removeFromIndividual(id, event.productIds()), failures);
        }
        for (Long id : salesStopUseCase.findSharedTargets(event.productIds())) {
            run(event, "공용물품 공동구매", id, () -> salesStopUseCase.removeFromShared(id, event.productIds()), failures);
        }
        if (!failures.isEmpty()) {
            throw failures.getFirst();
        }
    }

    private void run(SalesPostHiddenEvent event, String label, Long id, Runnable task, List<RuntimeException> failures) {
        try {
            task.run();
        } catch (ServiceException e) {
            log.info("판매글 #{} 숨김: {} #{} 정리 안 함 ({})", event.postId(), label, id, e.getMessage());
        } catch (RuntimeException e) {
            log.error("판매글 #{} 숨김: {} #{} 정리 실패, 인박스가 다시 처리", event.postId(), label, id, e);
            failures.add(e);
        }
    }
}
