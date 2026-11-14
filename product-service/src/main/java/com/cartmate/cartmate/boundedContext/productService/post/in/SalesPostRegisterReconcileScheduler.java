package com.cartmate.cartmate.boundedContext.productService.post.in;

import com.cartmate.cartmate.boundedContext.productService.post.app.SalesPostFacade;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 판매글 등록 대사(reconciliation).
 *
 * <p>등록이 실패하면 즉시 보상(판매글 삭제 + 상품 롤백)하지만, 등록 도중 서버가 멈추면 보상이 돌지 못해
 * 판매글이 등록 중(PENDING)으로 남고 상품 · 복제본 · 사진 파일도 남을 수 있다.
 * 등록을 시작한 지 30분이 지나도록 PENDING인 판매글을 같은 보상으로 정리한다(상품 먼저, 판매글 나중).
 *
 * <p>등록 중인 판매글은 사용자에게 보이지 않아 급하지 않으므로, 사용량이 적은 새벽에 하루 한 번 돈다.
 * 판매글마다 따로 커밋하고 처리한 판매글은 삭제되므로, 한 번에 많이 읽어도 큰 트랜잭션이나 재시작 지점이 필요 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cartmate.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SalesPostRegisterReconcileScheduler {

    static final String LOCK_NAME = "post.reconcileSalesPostRegister";
    private static final int BATCH_SIZE = 1000;

    private final SalesPostFacade salesPostFacade;

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = LOCK_NAME, lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    @Observed(name = "batch.post.register-reconcile") // 실행 1회 = span 하나 (스케줄 실행 관찰은 꺼 둠)
    public void reconcile() {
        for (Long postId : salesPostFacade.findStaleRegisteringPostIds(BATCH_SIZE)) {
            try {
                salesPostFacade.cleanUpStaleRegistering(postId);
                log.warn("등록 중에 멈춘 판매글 정리: postId={}", postId);
            } catch (RuntimeException e) {
                log.warn("등록 중에 멈춘 판매글 정리 실패, 다음 실행에서 다시 시도: postId={}", postId, e);
            }
        }
    }
}
