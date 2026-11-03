package com.cartmate.cartmate.boundedContext.trust.app;

import com.cartmate.cartmate.boundedContext.trust.domain.TrustMetrics;
import com.cartmate.cartmate.boundedContext.trust.out.SellerTrustHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SellerTrustMetricsAggregateUseCase {

    private final SellerTrustHistoryRepository sellerTrustHistoryRepository;

    // 판매자별 구매확정·귀책 클레임 건수를 한 번의 쿼리로 센다. 거래 이력이 없는 판매자는 결과에 나오지 않는다.
    // 건수는 long이라 TrustMetrics의 int 범위를 넘으면 예외가 난다.
    public Map<Long, TrustMetrics> aggregateAll() {
        Map<Long, TrustMetrics> metricsBySeller = new LinkedHashMap<>();
        sellerTrustHistoryRepository.aggregateBySeller().forEach(row -> metricsBySeller.put(
                row.getSellerId(),
                new TrustMetrics(
                        Math.toIntExact(row.getConfirmedCount()),
                        Math.toIntExact(row.getFaultClaimCount()))));
        return metricsBySeller;
    }
}
