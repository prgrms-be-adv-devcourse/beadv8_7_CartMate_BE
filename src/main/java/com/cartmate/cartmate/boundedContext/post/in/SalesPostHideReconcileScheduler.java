package com.cartmate.cartmate.boundedContext.post.in;

import com.cartmate.cartmate.boundedContext.post.app.SalesPostHideUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 판매글 숨김 후처리 대사(reconciliation).
 *
 * <p>최근 내려간 판매글의 숨김 이벤트를 주기적으로 다시 발행한다. 후처리는 전부 멱등이라
 * 이미 정리된 건은 건너뛰고, 다음 경우로 놓친 것만 처리된다.
 * <ul>
 *   <li>처리 중 서버 종료로 이벤트 유실</li>
 *   <li>처리 중 예외 (락 대기 초과 등)</li>
 *   <li>숨김과 결제가 동시에 일어나 대상 조회 시점에 아직 결제 전이었던 주문</li>
 * </ul>
 * Kafka 전환 시 Outbox + 재시도로 대체한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cartmate.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SalesPostHideReconcileScheduler {

    private static final long INTERVAL_MILLIS = 5 * 60_000L;

    private final SalesPostHideUseCase salesPostHideUseCase;

    @Scheduled(fixedDelay = INTERVAL_MILLIS, initialDelay = INTERVAL_MILLIS)
    public void reconcile() {
        int count = salesPostHideUseCase.republishRecentlyHidden();
        if (count > 0) {
            log.info("판매글 숨김 대사: {}건 재발행", count);
        }
    }
}