package com.cartmate.cartmate.boundedContext.productService.post.app;

import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostProduct;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostSaleStatus;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateProductOption;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateSalesPostProduct;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateSalesPostProductImage;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateSalesPostRequest;
import com.cartmate.cartmate.boundedContext.productService.post.in.SalesPostDeleteReconcileScheduler;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductImageRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductOptionRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostRepository;
import com.cartmate.cartmate.boundedContext.productService.product.app.TestImageFiles;
import com.cartmate.cartmate.boundedContext.productService.product.domain.Product;
import com.cartmate.cartmate.boundedContext.productService.product.out.FakeImageStorage;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductImageRepository;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductOptionRepository;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductRepository;
import com.cartmate.cartmate.global.outbox.ManualOutboxRelayConfig;
import com.cartmate.cartmate.global.outbox.OutboxEventStatus;
import com.cartmate.cartmate.global.outbox.OutboxRelay;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductStatus;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostProductStatus;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.time.LocalDateTime;
import java.util.List;

import static com.cartmate.cartmate.boundedContext.productService.post.domain.QSalesPost.salesPost;
import static com.cartmate.cartmate.global.inbox.QProcessedEvent.processedEvent;
import static com.cartmate.cartmate.global.outbox.QOutboxEvent.outboxEvent;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 판매글 삭제 대사 스케줄러: outbox 재시도를 다 써서 DELETING에 오래 멈춘 판매글의 삭제 이벤트를 다시 발행해 삭제를 끝낸다.
 *
 * <p>테스트는 스케줄링을 끄므로 스케줄러를 직접 만들어 부르고, outbox 릴레이도 직접 돌린다.
 * "오래 멈춘" 상태는 판매글의 삭제 시작 시각(modifyDate)을 2시간 전으로, 멈춘 outbox 행을 FAILED로 바꿔 만든다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {
        "cartmate.scheduling.enabled=false",
        // ManualOutboxRelayConfig의 릴레이도 @Scheduled가 붙어 있어 저절로 돈다. 테스트가 직접 돌리도록 첫 실행을 미룬다(order-service와 같은 방식)
        "custom.outbox.relay.initial-delay=PT1H"
})
@ActiveProfiles("test")
@Import(ManualOutboxRelayConfig.class)
class SalesPostDeleteReconcileTest {

    private static final int PORT = freePort();
    private static final Long SELLER_ID = 7L;

    private static final String SALES_POST_DELETED = "SalesPostDeletedEvent";
    private static final String PRODUCTS_DELETED_BY_POST = "ProductsDeletedByPostEvent";

    @DynamicPropertySource
    static void serverAndProductUrl(DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> PORT);
        registry.add("product.global.internalUrl", () -> "http://localhost:" + PORT);
    }

    @Autowired
    SalesPostFacade salesPostFacade;
    @Autowired
    OutboxRelay outboxRelay;
    @Autowired
    JdbcTemplate jdbcTemplate; // global의 릴레이 잠금 풀기(ManualOutboxRelayConfig)에만 넘긴다
    @Autowired
    JPAQueryFactory queryFactory;
    @Autowired
    TransactionTemplate transactionTemplate;

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

    SalesPostDeleteReconcileScheduler scheduler;
    Long postId;

    // 판매글 + 상품 2개를 실제 등록 흐름으로 만들어 둔다 (post 복제본까지 생성됨)
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

        scheduler = new SalesPostDeleteReconcileScheduler(salesPostFacade);
        salesPostFacade.registerSalesPost(request(), List.of(TestImageFiles.jpeg(), TestImageFiles.png()));
        postId = salesPostRepository.findAll().getFirst().getId();
    }

    @Test
    @DisplayName("상품 삭제 전에 멈춘 판매글은 삭제 이벤트를 다시 발행해 삭제를 끝낸다")
    void finishWhenStuckBeforeProductDelete() {
        salesPostFacade.deleteSalesPostByUser(postId, SELLER_ID);
        giveUp(SALES_POST_DELETED); // product에 한 번도 전달되지 못했다
        deleteStartedHoursAgo(2);

        scheduler.reconcile();
        relay(); // 다시 발행한 판매글 삭제 → product 상품 삭제
        relay(); // 상품 삭제 완료 → post 삭제 완료

        assertDeletedEverything();
    }

    @Test
    @DisplayName("상품은 지웠는데 완료 전에 멈춘 판매글도 삭제 이벤트를 다시 발행해 삭제를 끝낸다")
    void finishWhenStuckAfterProductDelete() {
        salesPostFacade.deleteSalesPostByUser(postId, SELLER_ID);
        relay(); // product 상품 삭제까지는 됐다
        giveUp(PRODUCTS_DELETED_BY_POST); // post에 완료가 전달되지 못했다
        deleteStartedHoursAgo(2);

        scheduler.reconcile();
        relay(); // product가 새 이벤트로 다시 처리(이미 지운 상품이라 결과 같음) → 상품 삭제 완료를 새로 발행
        relay(); // post 삭제 완료

        assertDeletedEverything();
    }

    // 1시간 안이면 outbox가 아직 재시도 중일 수 있으므로 기다린다
    @Test
    @DisplayName("삭제를 시작한 지 1시간이 안 된 판매글은 건드리지 않는다")
    void ignoreRecentDeleting() {
        salesPostFacade.deleteSalesPostByUser(postId, SELLER_ID);

        scheduler.reconcile();

        assertThat(outboxCount(SALES_POST_DELETED)).isEqualTo(1);
    }

    @Test
    @DisplayName("다시 발행하면 삭제 시작 시각이 다시 잡혀, 바로 다음 실행에서는 또 발행하지 않는다")
    void republishOnlyOnceUntilStuckAgain() {
        salesPostFacade.deleteSalesPostByUser(postId, SELLER_ID);
        giveUp(SALES_POST_DELETED);
        deleteStartedHoursAgo(2);

        scheduler.reconcile();
        scheduler.reconcile();

        // 처음 1개 + 다시 발행 1개
        assertThat(outboxCount(SALES_POST_DELETED)).isEqualTo(2);
        assertThat(salesPostRepository.findById(postId).orElseThrow().getStatus()).isEqualTo(SalesPostSaleStatus.DELETING);
    }

    private void assertDeletedEverything() {
        assertThat(salesPostRepository.findById(postId).orElseThrow().getStatus()).isEqualTo(SalesPostSaleStatus.DELETED);
        assertThat(productRepository.findAll()).extracting(Product::getStatus).containsOnly(ProductStatus.DELETED);
        assertThat(salesPostProductRepository.findAll())
                .hasSize(2)
                .extracting(SalesPostProduct::getStatus)
                .containsOnly(SalesPostProductStatus.DELETED);
    }

    // 재시도를 모두 써서 릴레이가 더는 보내지 않는 상태로 만든다
    private void giveUp(String eventType) {
        transactionTemplate.executeWithoutResult(status -> queryFactory.update(outboxEvent)
                .set(outboxEvent.status, OutboxEventStatus.FAILED)
                .where(outboxEvent.eventType.eq(eventType))
                .execute());
    }

    private void deleteStartedHoursAgo(int hours) {
        transactionTemplate.executeWithoutResult(status -> queryFactory.update(salesPost)
                .set(salesPost.modifyDate, LocalDateTime.now().minusHours(hours))
                .where(salesPost.id.eq(postId))
                .execute());
    }

    private void relay() {
        ManualOutboxRelayConfig.releaseRelayLock(jdbcTemplate);
        outboxRelay.relay();
    }

    private long outboxCount(String eventType) {
        return queryFactory.select(outboxEvent.count())
                .from(outboxEvent)
                .where(outboxEvent.eventType.eq(eventType))
                .fetchOne();
    }

    // 상품 2개, 각각 사진 1장 · 옵션 1개
    private CreateSalesPostRequest request() {
        CreateSalesPostProduct iphone = new CreateSalesPostProduct(null, null, 1L, "아이폰", "설명", 10000L,
                List.of(new CreateSalesPostProductImage(null, null, null, 0, null)),
                List.of(new CreateProductOption(1L, 100L)));
        CreateSalesPostProduct charger = new CreateSalesPostProduct(null, null, 1L, "충전기", "설명", 3000L,
                List.of(new CreateSalesPostProductImage(null, null, null, 0, null)),
                List.of(new CreateProductOption(2L, 200L)));

        return new CreateSalesPostRequest(null, SELLER_ID, "아이폰 팝니다", "상태 좋아요", List.of(iphone, charger));
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
