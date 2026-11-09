package com.cartmate.cartmate.boundedContext.cart.domain.matching;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductSimilarityTest {

    @Test
    @DisplayName("유사도는 0 이상 1 이하만 허용한다")
    void similarityRange() {
        assertThatThrownBy(() -> new ProductSimilarity(1L, 1.01, MatchType.VECTOR))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProductSimilarity(1L, -0.1, MatchType.VECTOR))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProductSimilarity(1L, Double.NaN, MatchType.VECTOR))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("유사도가 높은 쪽이 더 강하고, 같으면 정확 매칭이 더 강하다")
    void stronger() {
        ProductSimilarity exact = ProductSimilarity.exact(1L);
        ProductSimilarity vectorOne = new ProductSimilarity(1L, 1.0, MatchType.VECTOR);
        ProductSimilarity vectorLow = new ProductSimilarity(1L, 0.8, MatchType.VECTOR);

        assertThat(exact.isStrongerThan(vectorOne)).isTrue();
        assertThat(vectorOne.isStrongerThan(exact)).isFalse();
        assertThat(vectorOne.isStrongerThan(vectorLow)).isTrue();
    }
}
