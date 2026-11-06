package com.cartmate.cartmate.boundedContext.orderService.cart.out.matching;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.ProductVector;
import com.cartmate.cartmate.support.CartMateJpaTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@CartMateJpaTest
class JpaProductVectorIndexTest {

    private static final String PRIMARY = "gemini-embedding-2";
    private static final String FALLBACK = "gemini-embedding-001";

    @Autowired
    private ProductVectorIndex index;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("저장한 벡터를 값 손실 없이 되읽고, 같은 상품을 다시 적재하면 모델 · 버전 · 차원까지 덮어쓴다")
    void roundTripAndReplace() {
        float[] values = {0.1f, -0.25f, Float.MIN_VALUE, 1e-8f, 3.4e38f};
        index.saveAll(List.of(new ProductVector(1L, 10L, FALLBACK, "rule-v1", values)));
        flushAndClear();

        ProductVector stored = index.find(1L).orElseThrow();
        assertThat(stored.values()).containsExactly(values);
        assertThat(stored.categoryId()).isEqualTo(10L);

        index.saveAll(List.of(new ProductVector(1L, 20L, PRIMARY, "rule-v2", new float[]{0.5f, 0.5f, 0.5f})));
        flushAndClear();

        ProductVector replaced = index.find(1L).orElseThrow();
        assertThat(replaced.model()).isEqualTo(PRIMARY);
        assertThat(replaced.textVersion()).isEqualTo("rule-v2");
        assertThat(replaced.categoryId()).isEqualTo(20L);
        assertThat(replaced.values()).containsExactly(0.5f, 0.5f, 0.5f);
    }

    @Test
    @DisplayName("같은 모델 · 텍스트 버전으로 적재된 상품만 최신으로 보고, 대체 모델 · 옛 버전 · 미적재는 다시 적재 대상이다")
    void findUpToDate() {
        index.saveAll(List.of(
                new ProductVector(1L, 10L, PRIMARY, "rule-v1", new float[]{1f, 0f}),
                new ProductVector(2L, 10L, FALLBACK, "rule-v1", new float[]{1f, 0f}),
                new ProductVector(3L, 10L, PRIMARY, "rule-v0", new float[]{1f, 0f})));
        flushAndClear();

        assertThat(index.findUpToDate(List.of(1L, 2L, 3L, 4L), PRIMARY, "rule-v1")).containsExactly(1L);
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }
}
