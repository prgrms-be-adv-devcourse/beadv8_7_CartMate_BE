package com.cartmate.cartmate.boundedContext.productService.post.in;

import com.cartmate.cartmate.boundedContext.productService.post.app.SalesPostFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * 판매글 수정 복구 대사(reconciliation).
 *
 * <p>판매글 수정이 실패하면 즉시 복구(SalesPostRecoverUseCase.recover)를 하지만, 그마저 실패하거나 서버가 멈추면
 * 판매글이 수정 상태에 남아 다시 수정할 수 없다. 이 스케줄러가 남은 것을 마무리한다.
 * <ul>
 *   <li>복구 중(RECOVERING)에 멈춤: 복구를 다시 시도한다. 3번째 실패면 RECOVERY_FAILED</li>
 *   <li>수정 중(PENDING)에 서버 종료: 스냅샷이 있으면 복구, 없으면 수정 전 상태로 되돌린다</li>
 * </ul>
 *
 * <p>장애 뒤에는 대상이 한꺼번에 쌓일 수 있어
 * <ul>
 *   <li>시간 예산(TIME_BUDGET) 안에서는 100건씩 계속 처리한다. 잠금 최대 시간(5분)보다 짧게 잡아 실행이 잠금 안에서 끝나게 한다</li>
 *   <li>연속으로 실패하면(상품 서버 장애로 본다) 그 종류는 이번 실행에서 멈춘다. 장애 중에 복구 재시도 횟수를 다 써 버리지 않게</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cartmate.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SalesPostRecoverScheduler {

    static final String LOCK_NAME = "post.reconcileSalesPostRecover";
    private static final long INTERVAL_MILLIS = 5 * 60_000L;
    private static final int BATCH_SIZE = 100;
    static final Duration TIME_BUDGET = Duration.ofMinutes(4);
    static final int MAX_CONSECUTIVE_FAILURES = 3;

    private final SalesPostFacade salesPostFacade;

    @Scheduled(fixedDelay = INTERVAL_MILLIS, initialDelay = INTERVAL_MILLIS)
    @SchedulerLock(name = LOCK_NAME, lockAtMostFor = "PT5M", lockAtLeastFor = "PT5S")
    public void reconcile() {
        long deadline = System.nanoTime() + TIME_BUDGET.toNanos();
        drain(salesPostFacade::findStaleRecoveringPostIds, salesPostFacade::retryRecovering, "복구 재시도", deadline);
        drain(salesPostFacade::findStalePendingUpdatePostIds, salesPostFacade::recoverStalePendingUpdate, "멈춘 수정 정리", deadline);
    }

    // 시간 예산이 남아 있는 동안 ID 순서대로 100건씩 읽어 처리한다. 실패한 판매글은 상태가 그대로지만
    // 커서(마지막 ID) 다음부터 읽으므로 이번 실행에서 다시 시도하지 않고, 다음 실행에서 다시 대상이 된다
    private void drain(BiFunction<Long, Integer, List<Long>> finder, Consumer<Long> action, String name, long deadline) {
        long afterId = 0L;
        int consecutiveFailures = 0;

        while (System.nanoTime() < deadline) {
            List<Long> postIds = finder.apply(afterId, BATCH_SIZE);
            if (postIds.isEmpty()) {
                return;
            }
            for (Long postId : postIds) {
                if (System.nanoTime() >= deadline) {
                    log.warn("판매글 수정 복구 대사 - {}: 시간 예산을 다 써서 다음 실행에서 이어서 처리", name);
                    return;
                }
                afterId = postId;
                try {
                    action.accept(postId);
                    consecutiveFailures = 0;
                    log.warn("판매글 수정 복구 대사 - {}: postId={}", name, postId);
                } catch (RuntimeException e) {
                    consecutiveFailures++;
                    log.warn("판매글 수정 복구 대사 - {} 실패, 다음 실행에서 다시 시도: postId={}", name, postId, e);
                    if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                        log.error("판매글 수정 복구 대사 - {}: {}건 연속 실패해 이번 실행은 멈춤(상품 서버 장애 의심)",
                                name, consecutiveFailures);
                        return;
                    }
                }
            }
        }
    }
}
