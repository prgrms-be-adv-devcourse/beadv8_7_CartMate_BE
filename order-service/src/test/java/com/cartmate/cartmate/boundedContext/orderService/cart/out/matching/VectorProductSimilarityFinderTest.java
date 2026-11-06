package com.cartmate.cartmate.boundedContext.orderService.cart.out.matching;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.MatchType;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.ProductSimilarity;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.ProductVector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class VectorProductSimilarityFinderTest {

    private static final String MODEL = "gemini-embedding-2";
    private static final String TEXT = "rule-v1";

    private final InMemoryProductVectorIndex index = new InMemoryProductVectorIndex();
    private final VectorProductSimilarityFinder finder = new VectorProductSimilarityFinder(index, 0.8);

    @Test
    @DisplayName("같은 카테고리 · 같은 모델의 상품 중 기준값 이상만 VECTOR 유사도로 돌려주고 자기 자신은 뺀다")
    void similarInSameCategory() {
        index.saveAll(List.of(
                vector(1L, 10L, MODEL, 1f, 0f),
                vector(2L, 10L, MODEL, 0.9f, 0.1f),
                vector(3L, 10L, MODEL, 0f, 1f),
                vector(4L, 20L, MODEL, 1f, 0f)));

        Map<Long, List<ProductSimilarity>> result = finder.findSimilar(List.of(1L), Set.of(1L, 2L, 3L, 4L));

        assertThat(result.get(1L)).extracting(ProductSimilarity::productId, ProductSimilarity::matchType)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(2L, MatchType.VECTOR));
        assertThat(result.get(1L).getFirst().similarity()).isGreaterThan(0.99);
    }

    @Test
    @DisplayName("모델이나 텍스트 버전이 다른 벡터는 비교하지 않는다")
    void differentModelOrTextVersion() {
        index.saveAll(List.of(
                vector(1L, 10L, MODEL, 1f, 0f),
                vector(2L, 10L, "gemini-embedding-001", 1f, 0f),
                new ProductVector(3L, 10L, MODEL, "llm-v1", new float[]{1f, 0f})));

        assertThat(finder.findSimilar(List.of(1L), Set.of(2L, 3L))).isEmpty();
    }

    @Test
    @DisplayName("벡터가 없는 상품은 결과가 없다")
    void noVector() {
        assertThat(finder.findSimilar(List.of(1L), Set.of(2L))).isEmpty();
    }

    private static ProductVector vector(Long productId, Long categoryId, String model, float x, float y) {
        return new ProductVector(productId, categoryId, model, TEXT, new float[]{x, y});
    }
}
