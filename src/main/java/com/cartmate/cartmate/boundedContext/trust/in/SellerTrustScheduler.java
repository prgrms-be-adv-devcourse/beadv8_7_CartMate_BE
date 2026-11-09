package com.cartmate.cartmate.boundedContext.trust.in;

import com.cartmate.cartmate.boundedContext.trust.app.TrustFacade;
import com.cartmate.cartmate.boundedContext.trust.domain.TrustMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SellerTrustScheduler {

    private final TrustFacade trustFacade;

    // 매일 새벽 3시에 거래 이력이 있는 모든 판매자의 신뢰도 점수를 다시 계산한다. 주기는 custom.trust.cron으로 바꿀 수 있다.
    // 집계는 한 번에 하고, 점수 저장은 판매자마다 따로 해 한 명이 실패해도 나머지는 계속 처리한다.
    @Scheduled(cron = "${custom.trust.cron:0 0 3 * * *}")
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
