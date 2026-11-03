package com.cartmate.cartmate.boundedContext.trust.in;

import com.cartmate.cartmate.boundedContext.trust.app.TrustFacade;
import com.cartmate.cartmate.boundedContext.trust.domain.TrustMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SellerTrustSchedulerTest {

    private final TrustFacade trustFacade = mock(TrustFacade.class);
    private final SellerTrustScheduler scheduler = new SellerTrustScheduler(trustFacade);

    @Test
    @DisplayName("집계된 모든 판매자의 점수를 다시 계산한다")
    void recalculatesEverySeller() {
        TrustMetrics first = new TrustMetrics(10, 0);
        TrustMetrics second = new TrustMetrics(20, 1);
        Map<Long, TrustMetrics> metricsBySeller = new LinkedHashMap<>();
        metricsBySeller.put(1L, first);
        metricsBySeller.put(2L, second);
        when(trustFacade.aggregateMetrics()).thenReturn(metricsBySeller);

        scheduler.recalculateAll();

        verify(trustFacade).recalculate(1L, first);
        verify(trustFacade).recalculate(2L, second);
    }

    @Test
    @DisplayName("한 판매자의 재계산이 실패해도 나머지 판매자는 계속 처리한다")
    void failureOfOneSellerDoesNotStopOthers() {
        TrustMetrics first = new TrustMetrics(10, 0);
        TrustMetrics second = new TrustMetrics(20, 1);
        Map<Long, TrustMetrics> metricsBySeller = new LinkedHashMap<>();
        metricsBySeller.put(1L, first);
        metricsBySeller.put(2L, second);
        when(trustFacade.aggregateMetrics()).thenReturn(metricsBySeller);
        doThrow(new IllegalStateException("저장 실패")).when(trustFacade).recalculate(1L, first);

        scheduler.recalculateAll();

        verify(trustFacade).recalculate(2L, second);
    }

    @Test
    @DisplayName("거래 이력이 있는 판매자가 없으면 재계산하지 않는다")
    void noSellers() {
        when(trustFacade.aggregateMetrics()).thenReturn(Map.of());

        scheduler.recalculateAll();

        verify(trustFacade, never()).recalculate(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
    }
}
