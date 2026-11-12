package com.cartmate.cartmate.boundedContext.etcService.trust.in;

import com.cartmate.cartmate.boundedContext.etcService.trust.app.TrustFacade;
import com.cartmate.cartmate.boundedContext.etcService.trust.domain.TrustMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SellerTrustScheduler {

    private static final String ZONE = "Asia/Seoul";
    private static final String LOCK_NAME = "sellerTrust.recalculateAll";

    private final TrustFacade trustFacade;

    // 매일 한국 시간 새벽 3시에 거래 이력이 있는 모든 판매자의 신뢰도 점수를 다시 계산한다. 주기는 custom.trust.cron으로 바꿀 수 있다.
    // 서버 시간대가 아니라 한국 시간 기준이다. 서버가 UTC면 새벽 3시가 한국 시간 낮 12시가 된다.
    // 집계는 한 번에 하고, 점수 저장은 판매자마다 따로 해 한 명이 실패해도 나머지는 계속 처리한다.
    // 같은 서비스가 여러 개 떠 있어도 한 곳에서만 돌린다. 서버 시계가 조금 어긋나도 다른 곳이 곧바로 다시 돌지 않게 최소 1분은 잡아 둔다.
    @Scheduled(cron = "${custom.trust.cron:0 0 3 * * *}", zone = ZONE)
    @SchedulerLock(name = LOCK_NAME, lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void recalculateAll() {
        Map<Long, TrustMetrics> metricsBySeller = trustFacade.aggregateMetrics();

        metricsBySeller.forEach((sellerId, metrics) -> {
            try {
                trustFacade.recalculate(sellerId, metrics);
            } catch (Exception e) {
                log.error("판매자 신뢰도 재계산 실패: sellerId={}", sellerId, e);
            }
        });
    }
}
