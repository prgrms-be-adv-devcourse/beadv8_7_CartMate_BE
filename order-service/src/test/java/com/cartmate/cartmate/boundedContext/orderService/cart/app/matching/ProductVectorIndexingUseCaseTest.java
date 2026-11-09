package com.cartmate.cartmate.boundedContext.orderService.cart.app.matching;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.ProductDemand;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.ProductToIndex;
import com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching.ProductVector;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.CartProductProfile;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.ProductClient;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.matching.CartDemandReader;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.matching.ProductEmbeddingClient;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.matching.ProductLabelClient;
import com.cartmate.cartmate.boundedContext.orderService.cart.out.matching.ProductVectorIndex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class ProductVectorIndexingUseCaseTest {

    private static final String PRIMARY = "gemini-embedding-2";
    private static final LocalDateTime SINCE = LocalDateTime.of(2026, 10, 7, 0, 0);

    private final List<String> embeddedTexts = new ArrayList<>();
    private final List<ProductVector> saved = new ArrayList<>();
    private final Set<Long> upToDate = new HashSet<>();
    private final List<Long> demandOrder = new ArrayList<>();
    private final ProductClient productClient = mock(ProductClient.class);

    private final ProductEmbeddingClient embeddingClient = new ProductEmbeddingClient() {
        @Override
        public Embeddings embed(List<String> texts) {
            embeddedTexts.addAll(texts);
            return new Embeddings(PRIMARY, texts.stream().map(text -> new float[]{text.length(), 1f}).toList());
        }

        @Override
        public String primaryModel() {
            return PRIMARY;
        }
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
            return model.equals(PRIMARY) && textVersion.equals(RuleBasedEmbeddingTextBuilder.VERSION)
                    ? upToDate : Set.of();
        }
    };

    private final CartDemandReader demandReader = new CartDemandReader() {
        @Override
        public List<ProductDemand> findRecentDemands(LocalDateTime since, int minMemberCount, int limit,
                                                     Collection<Long> excludedProductIds) {
            return List.of();
        }

        @Override
        public List<Long> findRecentProductIdsByDemand(LocalDateTime since, int limit) {
            return demandOrder.stream().limit(limit).toList();
        }
    };

    @Test
    @DisplayName("장바구니 수요가 많은 순으로, 지금 모델 · 텍스트 버전으로 적재되지 않은 상품만 카테고리와 함께 적재하고 정보가 없는 상품은 건너뛴다")
    void indexStaleCartProducts() {
        demandOrder.addAll(List.of(1L, 2L, 3L, 4L));
        upToDate.add(2L);
        given(productClient.findAllProductProfiles(List.of(1L, 3L, 4L))).willReturn(List.of(
                new CartProductProfile(3L, "풋살공 4호", 12L),
                new CartProductProfile(1L, "나이키 머큐리얼 축구화 FG 270", 10L)));

        int count = useCaseWith(new RuleBasedEmbeddingTextBuilder()).indexStaleCartProducts(SINCE);

        assertThat(count).isEqualTo(2);
        assertThat(saved).extracting(ProductVector::productId, ProductVector::categoryId)
                .containsExactly(tuple(1L, 10L), tuple(3L, 12L));
        assertThat(embeddedTexts).containsExactly("축구화", "풋살공");
    }

    @Test
    @DisplayName("한 번에 분당 무료 한도(100건)까지만 적재하고 나머지는 다음 실행으로 넘긴다")
    void capPerRun() {
        LongStream.rangeClosed(1, 150).forEach(demandOrder::add);
        given(productClient.findAllProductProfiles(anyCollection())).willAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            return ids.stream().map(id -> new CartProductProfile(id, "축구공 " + id, 1L)).toList();
        });

        int count = useCaseWith(new RuleBasedEmbeddingTextBuilder()).indexStaleCartProducts(SINCE);

        assertThat(count).isEqualTo(ProductVectorIndexingUseCase.MAX_PER_RUN);
        assertThat(saved.getLast().productId()).isEqualTo((long) ProductVectorIndexingUseCase.MAX_PER_RUN);
    }

    @Test
    @DisplayName("이름 · 카테고리를 못 구한 상품은 건너뛰고 다음 후보로 한도를 채워, 그런 상품이 앞에 쌓여도 적재가 멈추지 않는다")
    void skipUnindexableWithoutBlocking() {
        LongStream.rangeClosed(1, 150).forEach(demandOrder::add);
        given(productClient.findAllProductProfiles(anyCollection())).willAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            return ids.stream()
                    .filter(id -> id > 60)
                    .map(id -> new CartProductProfile(id, "축구공 " + id, id <= 80 ? null : 1L))
                    .toList();
        });

        int count = useCaseWith(new RuleBasedEmbeddingTextBuilder()).indexStaleCartProducts(SINCE);

        assertThat(count).isEqualTo(70);
        assertThat(saved).extracting(ProductVector::productId).first().isEqualTo(81L);
        assertThat(saved).extracting(ProductVector::productId).last().isEqualTo(150L);
    }

    @Test
    @DisplayName("LLM 라벨을 쓰도록 바꾸면 라벨을 임베딩하고, 라벨이 하나라도 비면 그 묶음 전체를 규칙 기반으로 만든다")
    void llmLabelWithWholeBatchFallback() {
        ProductLabelClient labels = names -> names.size() == 2 ? List.of("축구화", "정강이 보호대") : List.of("축구공", "");
        ProductVectorIndexingUseCase useCase = useCaseWith(new LlmLabelEmbeddingTextBuilder(labels));

        useCase.index(List.of(new ProductToIndex(1L, 10L, "나이키 축구화"), new ProductToIndex(2L, 11L, "신가드 L")));
        useCase.index(List.of(new ProductToIndex(3L, 12L, "스타 축구공 5호"), new ProductToIndex(4L, 12L, "몰텐 축구공"),
                new ProductToIndex(5L, 12L, "풋살공")));

        assertThat(embeddedTexts).containsExactly("축구화", "정강이 보호대", "축구공", "축구공", "풋살공");
        assertThat(saved).extracting(ProductVector::textVersion).containsExactly(
                LlmLabelEmbeddingTextBuilder.VERSION, LlmLabelEmbeddingTextBuilder.VERSION,
                RuleBasedEmbeddingTextBuilder.VERSION, RuleBasedEmbeddingTextBuilder.VERSION,
                RuleBasedEmbeddingTextBuilder.VERSION);
    }

    private ProductVectorIndexingUseCase useCaseWith(EmbeddingTextBuilder builder) {
        return new ProductVectorIndexingUseCase(builder, embeddingClient, index, demandReader, productClient);
    }
}
