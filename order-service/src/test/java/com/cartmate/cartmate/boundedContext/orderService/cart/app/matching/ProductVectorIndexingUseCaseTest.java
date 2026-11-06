package com.cartmate.cartmate.boundedContext.orderService.cart.app.matching;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.ProductToIndex;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.ProductVector;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.matching.ProductEmbeddingClient;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.matching.ProductLabelClient;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.matching.ProductVectorIndex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProductVectorIndexingUseCaseTest {

    private final List<String> embeddedTexts = new ArrayList<>();
    private final List<ProductVector> saved = new ArrayList<>();

    private final ProductEmbeddingClient embeddingClient = texts -> {
        embeddedTexts.addAll(texts);
        return new ProductEmbeddingClient.Embeddings("gemini-embedding-2",
                texts.stream().map(text -> new float[]{text.length(), 1f}).toList());
    };

    private final ProductVectorIndex index = new ProductVectorIndex() {
        @Override
        public void saveAll(Collection<ProductVector> vectors) {
            saved.addAll(vectors);
        }

        @Override
        public Optional<ProductVector> find(Long productId) {
            return Optional.empty();
        }

        @Override
        public List<ProductVector> findAll(Collection<Long> productIds) {
            return List.of();
        }

        @Override
        public Set<Long> findUpToDate(Collection<Long> productIds, String model, String textVersion) {
            return Set.of();
        }
    };

    @Test
    @DisplayName("규칙 기반으로 다듬은 텍스트를 임베딩해 상품 · 카테고리 · 모델 · 텍스트 버전과 함께 저장한다")
    void indexWithRuleBasedText() {
        ProductVectorIndexingUseCase useCase =
                new ProductVectorIndexingUseCase(new RuleBasedEmbeddingTextBuilder(), embeddingClient, index);

        int count = useCase.index(List.of(new ProductToIndex(1L, 10L, "나이키 머큐리얼 축구화 FG 270")));

        assertThat(count).isEqualTo(1);
        assertThat(embeddedTexts).containsExactly("축구화");
        ProductVector vector = saved.getFirst();
        assertThat(vector.productId()).isEqualTo(1L);
        assertThat(vector.categoryId()).isEqualTo(10L);
        assertThat(vector.model()).isEqualTo("gemini-embedding-2");
        assertThat(vector.textVersion()).isEqualTo(RuleBasedEmbeddingTextBuilder.VERSION);
    }

    @Test
    @DisplayName("LLM 라벨을 쓰도록 바꾸면 라벨을 임베딩하고, 라벨이 하나라도 비면 그 묶음 전체를 규칙 기반으로 만든다")
    void llmLabelWithWholeBatchFallback() {
        ProductLabelClient labels = names -> names.size() == 2 ? List.of("축구화", "정강이 보호대") : List.of("축구공", "");
        ProductVectorIndexingUseCase useCase =
                new ProductVectorIndexingUseCase(new LlmLabelEmbeddingTextBuilder(labels), embeddingClient, index);

        useCase.index(List.of(new ProductToIndex(1L, 10L, "나이키 축구화"), new ProductToIndex(2L, 11L, "신가드 L")));
        useCase.index(List.of(new ProductToIndex(3L, 12L, "스타 축구공 5호"), new ProductToIndex(4L, 12L, "몰텐 축구공"),
                new ProductToIndex(5L, 12L, "풋살공")));

        assertThat(embeddedTexts).containsExactly("축구화", "정강이 보호대", "축구공", "축구공", "축구공");
        assertThat(saved).extracting(ProductVector::textVersion).containsExactly(
                LlmLabelEmbeddingTextBuilder.VERSION, LlmLabelEmbeddingTextBuilder.VERSION,
                RuleBasedEmbeddingTextBuilder.VERSION, RuleBasedEmbeddingTextBuilder.VERSION,
                RuleBasedEmbeddingTextBuilder.VERSION);
    }

    @Test
    @DisplayName("색인할 상품이 없으면 임베딩을 부르지 않는다")
    void empty() {
        new ProductVectorIndexingUseCase(new RuleBasedEmbeddingTextBuilder(), embeddingClient, index).index(List.of());

        assertThat(embeddedTexts).isEmpty();
    }
}
