package com.cartmate.cartmate.boundedContext.productService.post.in;

import com.cartmate.cartmate.boundedContext.productService.post.app.SalesPostFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 판매글 삭제 대사(reconciliation).
 *
 * <p>삭제는 이벤트(outbox)로 이어지고 실패하면 재시도된다. 그래도 끝나지 않고 DELETING에 오래 멈춘 판매글의
 * 삭제 이벤트를 다시 발행한다. 다음 경우가 대상이다.
 * <ul>
 *   <li>outbox 재시도를 모두 실패해 FAILED가 된 경우</li>
 *   <li>Kafka 수신 재시도를 모두 실패해 DLT로 간 경우</li>
 * </ul>
 * 이후 단계는 모두 멱등이라 다시 돌려도 안전하다. 재발행은 무언가 실패했다는 뜻이라 error 로그를 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cartmate.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SalesPostDeleteReconcileScheduler {

    static final String LOCK_NAME = "post.reconcileSalesPostDelete";
    private static final long INTERVAL_MILLIS = 10 * 60_000L;
    private static final int BATCH_SIZE = 100;

    private final SalesPostFacade salesPostFacade;

    @Scheduled(fixedDelay = INTERVAL_MILLIS, initialDelay = INTERVAL_MILLIS)
    @SchedulerLock(name = LOCK_NAME, lockAtMostFor = "PT5M", lockAtLeastFor = "PT5S")
    public void reconcile() {
        for (Long postId : salesPostFacade.findStuckDeletingPostIds(BATCH_SIZE)) {
            try {
                if (salesPostFacade.republishStuckDelete(postId)) {
                    log.error("판매글 삭제가 끝나지 않아 삭제 이벤트를 다시 발행: postId={}", postId);
                }
            } catch (RuntimeException e) {
                log.warn("판매글 삭제 이벤트 재발행 실패, 다음 실행에서 다시 시도: postId={}", postId, e);
            }
        }
    }
}
