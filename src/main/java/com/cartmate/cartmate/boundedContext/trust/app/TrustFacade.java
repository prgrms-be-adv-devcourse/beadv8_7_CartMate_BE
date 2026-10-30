package com.cartmate.cartmate.boundedContext.trust.app;

import com.cartmate.cartmate.boundedContext.trust.domain.TrustMetrics;
import com.cartmate.cartmate.shared.delivery.event.DeliveryClaimCompletedEvent;
import com.cartmate.cartmate.shared.delivery.event.DeliveryConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class TrustFacade {

    private final SellerTrustHistoryRecordUseCase sellerTrustHistoryRecordUseCase;
    private final SellerTrustMetricsAggregateUseCase sellerTrustMetricsAggregateUseCase;
    private final SellerTrustRecalculateUseCase sellerTrustRecalculateUseCase;

    // 구매확정 이벤트를 받아 구매확정 이력을 남긴다.
    @Transactional
    public void recordPurchaseConfirmed(DeliveryConfirmedEvent event) {
        sellerTrustHistoryRecordUseCase.recordPurchaseConfirmed(event);
    }

    // 클레임 처리 완료 이벤트를 받아 판매자 귀책이면 귀책 클레임 이력을 남긴다.
    @Transactional
    public void recordClaimCompleted(DeliveryClaimCompletedEvent event) {
        sellerTrustHistoryRecordUseCase.recordClaimCompleted(event);
    }

    // 모든 판매자의 구매확정·귀책 클레임 건수를 센다.
    @Transactional(readOnly = true)
    public Map<Long, TrustMetrics> aggregateMetrics() {
        return sellerTrustMetricsAggregateUseCase.aggregateAll();
    }

    // 판매자 한 명의 점수를 다시 계산한다. 판매자마다 별도 트랜잭션이라 한 명이 실패해도 다른 판매자에 영향이 없다.
    @Transactional
    public void recalculate(Long sellerId, TrustMetrics metrics) {
        sellerTrustRecalculateUseCase.recalculate(sellerId, metrics);
    }
}
