package com.cartmate.cartmate.boundedContext.productService.product.in;

import com.cartmate.cartmate.boundedContext.productService.product.app.ProductFacade;
import io.micrometer.observation.annotation.Observed;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * 고아 사진 파일 정리: 저장소에는 있는데 DB에는 키가 없는 사진 파일을 지운다(ProductImageOrphanCleanUseCase).
 *
 * <p>사용자에게 보이지 않는 저장소 정리라 급하지 않고, 저장소 전체를 훑어 비용이 크므로 하루 한 번 새벽 5시에 돈다.
 * 새벽 4시의 등록 대사(SalesPostRegisterReconcileScheduler)가 남긴 파일까지 이어서 정리한다.
 *
 * <p>삭제는 되돌릴 수 없어 기본은 dry-run(지울 대상만 로그)이다. 로그로 결과를 확인한 뒤 IMAGE_CLEANUP_DRY_RUN=false로 켠다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "cartmate.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class ProductImageOrphanCleanScheduler {

    static final String LOCK_NAME = "product.cleanUpOrphanImages";

    private final ProductFacade productFacade;
    private final boolean dryRun;
    private final Duration gracePeriod;

    public ProductImageOrphanCleanScheduler(ProductFacade productFacade,
                                            @Value("${cartmate.image-cleanup.dry-run:true}") boolean dryRun,
                                            @Value("${cartmate.image-cleanup.grace-period:P1D}") Duration gracePeriod) {
        this.productFacade = productFacade;
        this.dryRun = dryRun;
        this.gracePeriod = gracePeriod;
    }

    // 파일이 많으면 저장소 목록 조회가 오래 걸릴 수 있어 잠금을 넉넉하게 잡는다
    @Scheduled(cron = "0 0 5 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = LOCK_NAME, lockAtMostFor = "PT1H", lockAtLeastFor = "PT1M")
    @Observed(name = "batch.product.image-cleanup") // 실행 1회 = span 하나 (스케줄 실행 관찰은 꺼 둠)
    public void cleanUp() {
        int orphanCount = productFacade.cleanUpOrphanImages(Instant.now().minus(gracePeriod), dryRun);
        if (orphanCount > 0) {
            log.warn("고아 사진 파일 {}개 {}", orphanCount, dryRun ? "발견(dry-run이라 지우지 않음)" : "정리");
        }
    }
}
