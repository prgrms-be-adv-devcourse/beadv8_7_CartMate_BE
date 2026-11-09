package com.cartmate.cartmate.boundedContext.trust.app;

import com.cartmate.cartmate.boundedContext.trust.domain.TrustMetrics;
import com.cartmate.cartmate.boundedContext.trust.out.SellerTrustHistoryRepository;
import com.cartmate.cartmate.boundedContext.trust.out.SellerTrustMetricsRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SellerTrustMetricsAggregateUseCaseTest {

    private final SellerTrustHistoryRepository repository = mock(SellerTrustHistoryRepository.class);
    private final SellerTrustMetricsAggregateUseCase useCase = new SellerTrustMetricsAggregateUseCase(repository);

    private SellerTrustMetricsRow row(long sellerId, long confirmed, long faultClaim) {
        SellerTrustMetricsRow row = mock(SellerTrustMetricsRow.class);
        when(row.getSellerId()).thenReturn(sellerId);
        when(row.getConfirmedCount()).thenReturn(confirmed);
        when(row.getFaultClaimCount()).thenReturn(faultClaim);
        return row;
    }

    @Test
    @DisplayName("판매자별 집계 행을 판매자 ID별 집계값으로 바꾼다")
    void aggregateAll() {
        // 행 목(mock)을 먼저 만든다. thenReturn 인자 안에서 만들면 바깥 when이 끝나기 전에 다른 stubbing이 시작되어 충돌한다.
        SellerTrustMetricsRow first = row(1L, 50, 5);
        SellerTrustMetricsRow second = row(2L, 12, 0);
        when(repository.aggregateBySeller()).thenReturn(List.of(first, second));

        Map<Long, TrustMetrics> result = useCase.aggregateAll();

        assertThat(result).containsOnlyKeys(1L, 2L);
        assertThat(result.get(1L)).isEqualTo(new TrustMetrics(50, 5));
        assertThat(result.get(2L)).isEqualTo(new TrustMetrics(12, 0));
    }

    @Test
    @DisplayName("거래 이력이 없으면 빈 맵이다")
    void aggregateAllEmpty() {
        when(repository.aggregateBySeller()).thenReturn(List.of());

        assertThat(useCase.aggregateAll()).isEmpty();
    }

    @Test
    @DisplayName("건수가 int 범위를 넘으면 예외가 난다")
    void aggregateAllOverflow() {
        SellerTrustMetricsRow overflowRow = row(1L, Integer.MAX_VALUE + 1L, 0);
        when(repository.aggregateBySeller()).thenReturn(List.of(overflowRow));

        assertThatThrownBy(useCase::aggregateAll).isInstanceOf(ArithmeticException.class);
    }
}
