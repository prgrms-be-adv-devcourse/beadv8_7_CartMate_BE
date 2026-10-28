package com.cartmate.cartmate.boundedContext.cart.app.matching;

import com.cartmate.cartmate.boundedContext.cart.domain.matching.DemandCandidate;
import com.cartmate.cartmate.boundedContext.cart.domain.matching.MatchType;
import com.cartmate.cartmate.boundedContext.cart.domain.matching.ProductDemand;
import com.cartmate.cartmate.boundedContext.cart.domain.matching.ProductSimilarity;
import com.cartmate.cartmate.boundedContext.cart.domain.matching.SimilarProductDemand;
import com.cartmate.cartmate.boundedContext.cart.out.matching.ProductSimilarityFinder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DemandAggregatorTest {

    private static final ProductSimilarityFinder EXACT = ProductSimilarityResolverTest.EXACT;

    @Test
    @DisplayName("정확 매칭만 있으면 상품별로 담은 회원 수를 세고 최소 인원 이상만 남긴다")
    void exactOnly() {
        DemandAggregator aggregator = aggregatorWith(EXACT);

        List<DemandCandidate> candidates = aggregator.aggregate(List.of(
                new ProductDemand(10L, Set.of(1L, 2L, 3L)),
                new ProductDemand(20L, Set.of(1L, 2L)),
                new ProductDemand(30L, Set.of(4L, 5L, 6L, 7L))
        ), 3);

        assertThat(candidates).extracting(DemandCandidate::productId).containsExactly(30L, 10L);
        assertThat(candidates.getFirst().exactMemberCount()).isEqualTo(4);
        assertThat(candidates.getFirst().similarMemberCount()).isZero();
        assertThat(candidates.getFirst().similarProducts()).isEmpty();
    }

    @Test
    @DisplayName("비슷한 상품을 담은 회원도 1명으로 세고, 정확히 담은 회원과 겹치면 한 번만 센다")
    void similarMembersCountedOnce() {
        ProductSimilarityFinder vector = similarTo(Map.of(10L, List.of(
                new ProductSimilarity(11L, 0.91, MatchType.VECTOR),
                new ProductSimilarity(12L, 0.86, MatchType.VECTOR)
        )));
        DemandAggregator aggregator = aggregatorWith(EXACT, vector);

        List<DemandCandidate> candidates = aggregator.aggregate(List.of(
                new ProductDemand(10L, Set.of(1L, 2L, 3L, 4L)),
                new ProductDemand(11L, Set.of(4L, 5L, 6L)),
                new ProductDemand(12L, Set.of(6L, 7L))
        ), 5);

        assertThat(candidates).hasSize(1);
        DemandCandidate candidate = candidates.getFirst();
        assertThat(candidate.productId()).isEqualTo(10L);
        assertThat(candidate.exactMemberCount()).isEqualTo(4);
        assertThat(candidate.similarMemberCount()).isEqualTo(3);
        assertThat(candidate.totalMemberCount()).isEqualTo(7);
        assertThat(candidate.similarProducts()).containsExactly(
                new SimilarProductDemand(11L, 0.91, MatchType.VECTOR, 3),
                new SimilarProductDemand(12L, 0.86, MatchType.VECTOR, 2)
        );
    }

    @Test
    @DisplayName("모인 인원이 같으면 정확히 담은 회원이 많은 상품이 앞선다")
    void tieBreakByExact() {
        ProductSimilarityFinder vector = similarTo(Map.of(20L, List.of(
                new ProductSimilarity(21L, 0.9, MatchType.VECTOR)
        )));
        DemandAggregator aggregator = aggregatorWith(EXACT, vector);

        List<DemandCandidate> candidates = aggregator.aggregate(List.of(
                new ProductDemand(10L, Set.of(1L, 2L, 3L)),
                new ProductDemand(20L, Set.of(4L, 5L)),
                new ProductDemand(21L, Set.of(6L))
        ), 3);

        assertThat(candidates).extracting(DemandCandidate::productId).containsExactly(10L, 20L);
    }

    @Test
    @DisplayName("담은 회원이 없는 상품은 후보도 유사 상품도 되지 않는다")
    void ignoreEmptyDemand() {
        ProductSimilarityFinder vector = similarTo(Map.of(10L, List.of(
                new ProductSimilarity(11L, 0.9, MatchType.VECTOR)
        )));
        DemandAggregator aggregator = aggregatorWith(EXACT, vector);

        List<DemandCandidate> candidates = aggregator.aggregate(List.of(
                new ProductDemand(10L, Set.of(1L)),
                new ProductDemand(11L, Set.of())
        ), 1);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().similarProducts()).isEmpty();
    }

    @Test
    @DisplayName("같은 상품의 수요가 나뉘어 들어오면 회원을 합쳐서 센다")
    void mergeDuplicateProduct() {
        DemandAggregator aggregator = aggregatorWith(EXACT);

        List<DemandCandidate> candidates = aggregator.aggregate(List.of(
                new ProductDemand(10L, Set.of(1L, 2L)),
                new ProductDemand(10L, Set.of(2L, 3L))
        ), 3);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().exactMemberCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("모인 인원과 정확 인원이 같으면 상품 ID 순이다")
    void tieBreakByProductId() {
        DemandAggregator aggregator = aggregatorWith(EXACT);

        List<DemandCandidate> candidates = aggregator.aggregate(List.of(
                new ProductDemand(30L, Set.of(1L, 2L)),
                new ProductDemand(20L, Set.of(3L, 4L))
        ), 2);

        assertThat(candidates).extracting(DemandCandidate::productId).containsExactly(20L, 30L);
    }

    @Test
    @DisplayName("입력이 비어 있으면 후보도 비어 있다")
    void emptyDemands() {
        assertThat(aggregatorWith(EXACT).aggregate(List.of(), 1)).isEmpty();
    }

    @Test
    @DisplayName("최소 인원은 1명 이상이어야 한다")
    void minMemberCount() {
        assertThatThrownBy(() -> aggregatorWith(EXACT).aggregate(List.of(), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static DemandAggregator aggregatorWith(ProductSimilarityFinder... finders) {
        return new DemandAggregator(new ProductSimilarityResolver(List.of(finders)));
    }

    private static ProductSimilarityFinder similarTo(Map<Long, List<ProductSimilarity>> similarities) {
        return (sources, candidates) -> similarities;
    }
}
