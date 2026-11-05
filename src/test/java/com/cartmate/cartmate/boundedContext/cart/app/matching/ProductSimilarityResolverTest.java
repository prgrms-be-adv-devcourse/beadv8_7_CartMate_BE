package com.cartmate.cartmate.boundedContext.cart.app.matching;

import com.cartmate.cartmate.boundedContext.cart.domain.matching.MatchType;
import com.cartmate.cartmate.boundedContext.cart.domain.matching.ProductSimilarity;
import com.cartmate.cartmate.boundedContext.cart.out.matching.ProductSimilarityFinder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ProductSimilarityResolverTest {

    static final ProductSimilarityFinder EXACT = (sources, candidates) -> sources.stream()
            .filter(candidates::contains)
            .collect(Collectors.toMap(id -> id, id -> List.of(ProductSimilarity.exact(id))));

    @Test
    @DisplayName("같은 상품을 여러 전략이 내면 가장 높은 유사도 하나만 남기고, 같으면 정확 매칭을 남긴다")
    void keepStrongest() {
        ProductSimilarityFinder vector = (sources, candidates) -> Map.of(1L, List.of(
                new ProductSimilarity(1L, 1.0, MatchType.VECTOR),
                new ProductSimilarity(2L, 0.82, MatchType.VECTOR),
                new ProductSimilarity(3L, 0.91, MatchType.VECTOR)
        ));
        ProductSimilarityFinder vectorAgain = (sources, candidates) -> Map.of(1L, List.of(
                new ProductSimilarity(2L, 0.88, MatchType.VECTOR)
        ));
        ProductSimilarityResolver resolver = new ProductSimilarityResolver(List.of(vector, EXACT, vectorAgain));

        assertThat(resolver.resolveAll(List.of(1L), Set.of(1L, 2L, 3L)).get(1L)).containsExactly(
                ProductSimilarity.exact(1L),
                new ProductSimilarity(3L, 0.91, MatchType.VECTOR),
                new ProductSimilarity(2L, 0.88, MatchType.VECTOR)
        );
    }

    @Test
    @DisplayName("후보에 자기 자신이 있으면 결과에 자기 자신(정확 매칭)이 포함된다")
    void includeSelf() {
        ProductSimilarityResolver resolver = new ProductSimilarityResolver(List.of(EXACT));

        assertThat(resolver.resolveAll(List.of(1L, 2L), Set.of(1L, 2L))).isEqualTo(Map.of(
                1L, List.of(ProductSimilarity.exact(1L)),
                2L, List.of(ProductSimilarity.exact(2L))
        ));
    }

    @Test
    @DisplayName("후보에 없는 상품과 요청하지 않은 기준 상품은 전략이 내더라도 버린다")
    void dropOutside() {
        ProductSimilarityFinder vector = (sources, candidates) -> Map.of(
                1L, List.of(new ProductSimilarity(99L, 0.95, MatchType.VECTOR)),
                77L, List.of(new ProductSimilarity(2L, 0.95, MatchType.VECTOR))
        );
        ProductSimilarityResolver resolver = new ProductSimilarityResolver(List.of(EXACT, vector));

        assertThat(resolver.resolveAll(List.of(1L), Set.of(1L, 2L)))
                .isEqualTo(Map.of(1L, List.of(ProductSimilarity.exact(1L))));
    }

    @Test
    @DisplayName("기준 상품이나 후보가 비어 있으면 전략을 부르지 않고 비어 있다")
    void emptyInput() {
        ProductSimilarityFinder failing = (sources, candidates) -> {
            throw new AssertionError("호출되면 안 된다");
        };
        ProductSimilarityResolver resolver = new ProductSimilarityResolver(List.of(failing));

        assertThat(resolver.resolveAll(List.of(1L), Set.of())).isEmpty();
        assertThat(resolver.resolveAll(List.of(), Set.of(1L))).isEmpty();
    }

    @Test
    @DisplayName("전략은 기준 상품 전체를 한 번에 받아 한 번만 호출된다")
    void calledOncePerFinder() {
        int[] calls = {0};
        ProductSimilarityFinder counting = (sources, candidates) -> {
            calls[0]++;
            return Map.of();
        };
        ProductSimilarityResolver resolver = new ProductSimilarityResolver(List.of(counting));

        resolver.resolveAll(List.of(1L, 2L, 3L), Set.of(1L, 2L, 3L));

        assertThat(calls[0]).isEqualTo(1);
    }
}
