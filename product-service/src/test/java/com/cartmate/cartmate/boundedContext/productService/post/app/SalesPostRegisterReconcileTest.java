package com.cartmate.cartmate.boundedContext.productService.post.app;

import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostSaleStatus;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateSalesPostRequest;
import com.cartmate.cartmate.boundedContext.productService.post.in.SalesPostRegisterReconcileScheduler;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductImageRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductOptionRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostRepository;
import com.cartmate.cartmate.boundedContext.productService.product.app.ProductFacade;
import com.cartmate.cartmate.boundedContext.productService.product.app.TestImageFiles;
import com.cartmate.cartmate.boundedContext.productService.product.dto.CreateProduct;
import com.cartmate.cartmate.boundedContext.productService.product.dto.CreateProductImage;
import com.cartmate.cartmate.boundedContext.productService.product.dto.CreateProductOption;
import com.cartmate.cartmate.boundedContext.productService.product.dto.CreateProductRequest;
import com.cartmate.cartmate.boundedContext.productService.product.dto.SyncProduct;
import com.cartmate.cartmate.boundedContext.productService.product.out.FakeImageStorage;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductImageRepository;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductOptionRepository;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductRepository;
import com.cartmate.cartmate.global.outbox.ManualOutboxRelayConfig;
import com.cartmate.cartmate.global.outbox.OutboxRelay;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static com.cartmate.cartmate.boundedContext.productService.post.domain.QSalesPost.salesPost;
import static com.cartmate.cartmate.global.inbox.QProcessedEvent.processedEvent;
import static com.cartmate.cartmate.global.outbox.QOutboxEvent.outboxEvent;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 판매글 등록 대사 스케줄러: 등록 도중 서버가 멈춰 등록 중(PENDING)으로 남은 판매글을 등록 실패 보상과 같이 정리한다.
 *
 * <p>"등록 중 멈춤"은 판매글 저장(PENDING)과 상품 등록까지만 하고 판매글 완료(ON_SALE)를 하지 않아 만든다.
 * "오래된" 것은 판매글 생성 시각(createDate)을 과거로 돌려 만든다. 테스트는 스케줄링을 끄므로 스케줄러를 직접 만들어 부른다.
 * 상품 정리는 등록 실패 이벤트(outbox)로 product에 전달되므로 릴레이도 직접 돌린다.
 */
@SpringBootTest(properties = {
        "cartmate.scheduling.enabled=false",
        // ManualOutboxRelayConfig의 릴레이도 @Scheduled가 붙어 있어 저절로 돈다. 테스트가 직접 돌리도록 첫 실행을 미룬다
        "custom.outbox.relay.initial-delay=PT1H"
})
@ActiveProfiles("test")
@Import(ManualOutboxRelayConfig.class)
class SalesPostRegisterReconcileTest {

    private static final Long SELLER_ID = 7L;

    @Autowired
    SalesPostFacade salesPostFacade;
    @Autowired
    SalesPostCreateUseCase salesPostCreateUseCase;
    @Autowired
    ProductFacade productFacade;
    @Autowired
    TransactionTemplate transactionTemplate;
    @Autowired
    SalesPostSyncProductUseCase salesPostSyncProductUseCase;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    JPAQueryFactory queryFactory;

    @Autowired
    SalesPostRepository salesPostRepository;
    @Autowired
    SalesPostProductRepository salesPostProductRepository;
    @Autowired
    SalesPostProductImageRepository salesPostProductImageRepository;
    @Autowired
    SalesPostProductOptionRepository salesPostProductOptionRepository;

    @Autowired
    ProductRepository productRepository;
    @Autowired
    ProductImageRepository productImageRepository;
    @Autowired
    ProductOptionRepository productOptionRepository;
    @Autowired
    FakeImageStorage storage;
    @Autowired
    OutboxRelay outboxRelay;
    @Autowired
    JdbcTemplate jdbcTemplate; // global의 릴레이 잠금 풀기(ManualOutboxRelayConfig)에만 넘긴다

    SalesPostRegisterReconcileScheduler scheduler;

    @BeforeEach
    void setUp() {
        salesPostProductImageRepository.deleteAll();
        salesPostProductOptionRepository.deleteAll();
        salesPostProductRepository.deleteAll();
        salesPostRepository.deleteAll();
        productImageRepository.deleteAll();
        productOptionRepository.deleteAll();
        productRepository.deleteAll();
        transactionTemplate.executeWithoutResult(status -> {
            queryFactory.delete(outboxEvent).execute();
            queryFactory.delete(processedEvent).execute();
        });
        storage.reset();

        scheduler = new SalesPostRegisterReconcileScheduler(salesPostFacade);
    }

    @Test
    @DisplayName("상품 등록 뒤 완료 전에 멈춰 오래 PENDING인 판매글은 판매글 · 상품 · 복제본 · 사진 파일이 모두 사라진다")
    void cleanUpStalePendingWithProducts() {
        Long postId = savePendingPost("아이폰 팝니다");
        registerProduct(postId);
        assertThat(productRepository.count()).isEqualTo(1);
        assertThat(salesPostProductRepository.count()).isZero(); // 복제본은 완료 트랜잭션에서 만들어지므로 아직 없다
        assertThat(storage.savedCount()).isEqualTo(2);
        createdMinutesAgo(postId, 31);

        scheduler.reconcile();
        relay(); // 등록 실패 이벤트 → product 상품 · 사진 정리

        assertThat(salesPostRepository.findById(postId)).isEmpty();
        assertThat(productRepository.count()).isZero();
        assertThat(productImageRepository.count()).isZero();
        assertThat(productOptionRepository.count()).isZero();
        assertThat(salesPostProductRepository.count()).isZero();
        assertThat(salesPostProductImageRepository.count()).isZero();
        assertThat(salesPostProductOptionRepository.count()).isZero();
        assertThat(storage.savedCount()).isZero();
    }

    @Test
    @DisplayName("상품 등록 전에 멈춰 오래 PENDING인 판매글은 판매글만 지운다")
    void cleanUpStalePendingWithoutProducts() {
        Long postId = savePendingPost("아이폰 팝니다");
        createdMinutesAgo(postId, 31);

        scheduler.reconcile();

        assertThat(salesPostRepository.findById(postId)).isEmpty();
    }

    // 등록이 아직 진행 중일 수 있으므로 기다린다
    @Test
    @DisplayName("만든 지 얼마 안 된 PENDING 판매글은 건드리지 않는다")
    void ignoreRecentPending() {
        Long postId = savePendingPost("아이폰 팝니다");
        registerProduct(postId);

        scheduler.reconcile();

        assertThat(salesPostRepository.findById(postId).orElseThrow().getStatus()).isEqualTo(SalesPostSaleStatus.PENDING);
        assertThat(productRepository.count()).isEqualTo(1);
        assertThat(storage.savedCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("등록이 끝난(ON_SALE) 판매글은 오래됐어도 건드리지 않는다")
    void ignoreCompleted() {
        Long postId = savePendingPost("아이폰 팝니다");
        List<SyncProduct> saved = registerProduct(postId);
        complete(postId, saved);
        createdMinutesAgo(postId, 31);

        scheduler.reconcile();

        assertThat(salesPostRepository.findById(postId).orElseThrow().getStatus()).isEqualTo(SalesPostSaleStatus.ON_SALE);
        assertThat(productRepository.count()).isEqualTo(1);
        assertThat(salesPostProductRepository.count()).isEqualTo(1);
        assertThat(storage.savedCount()).isEqualTo(2);
    }

    // 등록 흐름의 ① 판매글 저장(PENDING)
    private Long savePendingPost(String title) {
        SalesPost post = salesPostCreateUseCase.saveSalesPost(
                new CreateSalesPostRequest(null, SELLER_ID, title, "상태 좋아요", List.of()));
        return post.getId();
    }

    // 등록 흐름의 ② 상품 등록(사진 2장, 옵션 1개). 저장한 상품을 돌려준다(복제본은 아직 없음)
    private List<SyncProduct> registerProduct(Long postId) {
        CreateProduct product = new CreateProduct(null, SELLER_ID, postId, 1L, "아이폰", "설명", 10000L,
                List.of(new CreateProductImage(null, SELLER_ID, null, null, 0, null),
                        new CreateProductImage(null, SELLER_ID, null, null, 1, null)),
                List.of(new CreateProductOption(1L, 100L)));
        return productFacade.registerProduct(new CreateProductRequest(postId, List.of(product)),
                List.of(TestImageFiles.jpeg(), TestImageFiles.png()));
    }

    // 등록 흐름의 ③ 완료: post가 상품 등록 응답으로 복제본을 만들고 판매를 시작한다(한 트랜잭션)
    private void complete(Long postId, List<SyncProduct> saved) {
        transactionTemplate.executeWithoutResult(s -> {
            salesPostSyncProductUseCase.syncProduct(objectMapper.convertValue(saved,
                    new TypeReference<List<com.cartmate.cartmate.boundedContext.productService.post.dto.SyncProduct>>() {}));
            salesPostCreateUseCase.completeSave(postId);
        });
    }

    private void relay() {
        ManualOutboxRelayConfig.releaseRelayLock(jdbcTemplate);
        outboxRelay.relay();
    }

    private void createdMinutesAgo(Long postId, int minutes) {
        transactionTemplate.executeWithoutResult(s -> queryFactory.update(salesPost)
                .set(salesPost.createDate, LocalDateTime.now().minusMinutes(minutes))
                .where(salesPost.id.eq(postId))
                .execute());
    }
}
