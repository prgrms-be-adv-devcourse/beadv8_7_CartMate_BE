package com.cartmate.cartmate.boundedContext.orderService.cart.out.matching;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.ProductSimilarity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ExactProductSimilarityFinderTest {

    private final ExactProductSimilarityFinder finder = new ExactProductSimilarityFinder();

    @Test
    @DisplayName("후보에 같은 상품이 있는 상품만 유사도 1.0 정확 매칭 하나를 돌려준다")
    void sameProductOnly() {
        assertThat(finder.findSimilar(List.of(10L, 20L, 10L), Set.of(10L, 11L)))
                .isEqualTo(Map.of(10L, List.of(ProductSimilarity.exact(10L))));
    }

    @Test
    @DisplayName("후보에 같은 상품이 없으면 비어 있다")
    void noSameProduct() {
        assertThat(finder.findSimilar(List.of(10L), Set.of(11L, 12L))).isEmpty();
    }
}
