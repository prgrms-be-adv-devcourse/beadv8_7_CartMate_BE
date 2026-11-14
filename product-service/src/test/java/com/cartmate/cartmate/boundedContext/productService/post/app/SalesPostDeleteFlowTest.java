package com.cartmate.cartmate.boundedContext.productService.post.app;

import static com.cartmate.cartmate.support.TestInventories.inventory;
import static com.cartmate.cartmate.support.SeedOptions.*;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostProduct;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostProductImage;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostProductOption;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostSaleStatus;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostUpdateStatus;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateProductOption;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateSalesPostProduct;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateSalesPostProductImage;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateSalesPostRequest;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductImageRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductOptionRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostRepository;
import com.cartmate.cartmate.boundedContext.productService.product.app.TestImageFiles;
import com.cartmate.cartmate.boundedContext.productService.product.domain.Product;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductImage;
import com.cartmate.cartmate.boundedContext.productService.product.domain.Inventory;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductOption;
import com.cartmate.cartmate.boundedContext.productService.product.out.FakeImageStorage;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductImageRepository;
import com.cartmate.cartmate.boundedContext.productService.product.out.InventoryRepository;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductOptionRepository;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.outbox.ManualOutboxRelayConfig;
import com.cartmate.cartmate.global.outbox.OutboxRelay;
import com.cartmate.cartmate.global.outbox.OutboxEventStatus;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.cartmate.cartmate.shared.post.event.SalesPostHiddenEvent;
import com.cartmate.cartmate.shared.post.event.SalesPostHideType;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductStatus;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostProductStatus;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.time.LocalDateTime;
import java.util.List;

import static com.cartmate.cartmate.global.inbox.QProcessedEvent.processedEvent;
import static com.cartmate.cartmate.global.outbox.QOutboxEvent.outboxEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

/**
 * 판매글 삭제: post DELETING + SalesPostDeletedEvent(outbox)
 * → product 상품 삭제 + ProductsDeletedByPostEvent(outbox)
 * → post 복제본 삭제 + 판매글 DELETED + 진행 중 거래 정리를 위한 숨김 이벤트(order-service가 받음).
 *
 * <p>테스트는 스케줄링을 끄므로 outbox 릴레이를 직접 돌린다. Kafka는 꺼져 있어 같은 앱 안으로 전달된다.
 * 판매글 · 상품 · 복제본은 실제 등록 흐름(상품 API를 실제 HTTP로 부름)으로 만들어 두므로 웹 서버를 빈 포트로 띄운다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {
        "cartmate.scheduling.enabled=false",
        // ManualOutboxRelayConfig의 릴레이도 @Scheduled가 붙어 있어 저절로 돈다. 테스트가 직접 돌리도록 첫 실행을 미룬다(order-service와 같은 방식)
        "custom.outbox.relay.initial-delay=PT1H"
})
@ActiveProfiles("test")
@RecordApplicationEvents
@Import(ManualOutboxRelayConfig.class)
class SalesPostDeleteFlowTest {

    private static final int PORT = freePort();
    private static final Long SELLER_ID = 7L;
    private static final Long OTHER_SELLER_ID = 8L;

    private static final String SALES_POST_DELETED = "SalesPostDeletedEvent";
    private static final String PRODUCTS_DELETED_BY_POST = "ProductsDeletedByPostEvent";

    @DynamicPropertySource
    static void serverAndProductUrl(DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> PORT);
        registry.add("product.global.internalUrl", () -> "http://localhost:" + PORT);
    }

    @Autowired
    SalesPostFacade salesPostFacade;
    @MockitoSpyBean
    SalesPostDeleteUseCase salesPostDeleteUseCase;
    @Autowired
    OutboxRelay outboxRelay;
    @Autowired
    JdbcTemplate jdbcTemplate; // global의 릴레이 잠금 풀기(ManualOutboxRelayConfig)에만 넘긴다
    @Autowired
    JPAQueryFactory queryFactory;
    @Autowired
    ApplicationEvents events;
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
    InventoryRepository inventoryRepository;
    @Autowired
    FakeImageStorage storage;

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
        reset(salesPostDeleteUseCase);

        salesPostFacade.registerSalesPost(request(), List.of(TestImageFiles.jpeg(), TestImageFiles.png()));
        postId = salesPostRepository.findAll().getFirst().getId();
    }

    @Test
    @DisplayName("삭제 요청 직후에는 판매글만 DELETING이 되고, 상품 삭제는 이벤트가 전달된 뒤에 일어난다")
    void onlyDeletingUntilRelayed() {
        salesPostFacade.deleteSalesPostByUser(postId, SELLER_ID);

        assertThat(salesPostRepository.findById(postId).orElseThrow().getStatus()).isEqualTo(SalesPostSaleStatus.DELETING);
        assertThat(productRepository.findAll()).extracting(Product::getStatus).containsOnly(ProductStatus.ON_SALE);
        assertThat(outboxCount(SALES_POST_DELETED, OutboxEventStatus.NEW)).isEqualTo(1);
    }

    @Test
    @DisplayName("이벤트가 전달되면 판매글 · 상품 · 복제본이 모두 삭제 상태가 된다")
    void deleteSalesPostDeletesEverything() {
        salesPostFacade.deleteSalesPostByUser(postId, SELLER_ID);
        relay(); // 판매글 삭제 → product 상품 삭제
        relay(); // 상품 삭제 완료 → post 복제본 삭제 · 판매글 삭제 완료

        SalesPost post = salesPostRepository.findById(postId).orElseThrow();
        assertThat(post.getStatus()).isEqualTo(SalesPostSaleStatus.DELETED);
        assertThat(post.getHiddenAt()).isNotNull();

        assertThat(productRepository.findAll())
                .hasSize(2)
                .extracting(Product::getStatus)
                .containsOnly(ProductStatus.DELETED);
        assertThat(productImageRepository.findAll())
                .extracting(ProductImage::getStatus)
                .containsOnly(ProductStatus.DELETED);
        // 재고 · 선택된 옵션은 논리 삭제라 행이 남고 상태만 DELETED (상품 2개 × 재고 1개, 재고마다 선택된 옵션 1개)
        assertThat(inventoryRepository.findAll())
                .hasSize(2)
                .extracting(Inventory::getStatus)
                .containsOnly(ProductStatus.DELETED);
        assertThat(productOptionRepository.findAll())
                .hasSize(2)
                .extracting(ProductOption::getStatus)
                .containsOnly(ProductStatus.DELETED);

        assertThat(salesPostProductRepository.findAll())
                .hasSize(2)
                .extracting(SalesPostProduct::getStatus)
                .containsOnly(SalesPostProductStatus.DELETED);
        assertThat(salesPostProductImageRepository.findAll())
                .extracting(SalesPostProductImage::getStatus)
                .containsOnly(SalesPostProductStatus.DELETED);
        assertThat(salesPostProductOptionRepository.findAll())
                .hasSize(2)
                .extracting(SalesPostProductOption::getStatus)
                .containsOnly(SalesPostProductStatus.DELETED);

        assertThat(outboxCount(SALES_POST_DELETED, OutboxEventStatus.PUBLISHED)).isEqualTo(1);
        assertThat(outboxCount(PRODUCTS_DELETED_BY_POST, OutboxEventStatus.PUBLISHED)).isEqualTo(1);
    }

    @Test
    @DisplayName("삭제가 끝나면 진행 중 거래 정리를 위한 숨김 이벤트가 나간다")
    void publishHiddenEventAfterDelete() {
        List<Long> copyIds = salesPostProductRepository.findAll().stream()
                .map(SalesPostProduct::getId)
                .toList();

        salesPostFacade.deleteSalesPostByUser(postId, SELLER_ID);
        relay();
        relay();

        List<SalesPostHiddenEvent> hidden = events.stream(SalesPostHiddenEvent.class).toList();
        assertThat(hidden).hasSize(1);
        SalesPostHiddenEvent event = hidden.getFirst();
        assertThat(event.postId()).isEqualTo(postId);
        assertThat(event.sellerId()).isEqualTo(SELLER_ID);
        assertThat(event.hideType()).isEqualTo(SalesPostHideType.SELLER_DELETED);
        assertThat(event.productIds()).hasSize(2).containsExactlyInAnyOrderElementsOf(copyIds);
    }

    // 릴레이가 PUBLISHED 기록에 실패하면 같은 이벤트를 다시 보낸다. 받는 쪽은 한 번만 처리해야 한다
    @Test
    @DisplayName("같은 이벤트가 다시 와도 한 번만 처리된다")
    void sameEventIsProcessedOnce() {
        salesPostFacade.deleteSalesPostByUser(postId, SELLER_ID);
        relay();
        relay();

        // 보낸 이벤트를 모두 다시 보낼 상태로 되돌린다
        transactionTemplate.executeWithoutResult(status -> queryFactory.update(outboxEvent)
                .set(outboxEvent.status, OutboxEventStatus.NEW)
                .setNull(outboxEvent.publishedAt)
                .execute());
        relay();
        relay();

        // product가 다시 처리했다면 상품 삭제 완료 이벤트가 하나 더 생긴다
        assertThat(outboxCount(PRODUCTS_DELETED_BY_POST, null)).isEqualTo(1);
        assertThat(events.stream(SalesPostHiddenEvent.class)).hasSize(1);
        assertThat(salesPostRepository.findById(postId).orElseThrow().getStatus()).isEqualTo(SalesPostSaleStatus.DELETED);
    }

    @Test
    @DisplayName("삭제 완료 처리에서 실패하면 되돌려지고, 릴레이가 다시 보내 삭제를 끝낸다")
    void retryAfterCompleteFails() {
        doThrow(new RuntimeException("테스트용 삭제 완료 실패"))
                .doCallRealMethod()
                .when(salesPostDeleteUseCase).completeDelete(anyLong());

        salesPostFacade.deleteSalesPostByUser(postId, SELLER_ID);
        relay(); // product 상품 삭제
        relay(); // post 처리 중 실패

        // 복제본 삭제 · 처리 기록이 함께 롤백되고, 이벤트는 다시 보낼 상태로 남는다
        assertThat(salesPostRepository.findById(postId).orElseThrow().getStatus()).isEqualTo(SalesPostSaleStatus.DELETING);
        assertThat(salesPostProductRepository.findAll()).extracting(SalesPostProduct::getStatus).containsOnly(SalesPostProductStatus.ON_SALE);
        assertThat(queryFactory.select(outboxEvent.attempts)
                .from(outboxEvent)
                .where(outboxEvent.eventType.eq(PRODUCTS_DELETED_BY_POST),
                        outboxEvent.status.eq(OutboxEventStatus.NEW))
                .fetchOne()).isEqualTo(1);

        // 다음 시도 시각을 기다리지 않고 바로 다시 보낸다
        transactionTemplate.executeWithoutResult(status -> queryFactory.update(outboxEvent)
                .set(outboxEvent.nextAttemptAt, LocalDateTime.now().minusMinutes(1))
                .where(outboxEvent.status.eq(OutboxEventStatus.NEW))
                .execute());
        relay();

        assertThat(salesPostRepository.findById(postId).orElseThrow().getStatus()).isEqualTo(SalesPostSaleStatus.DELETED);
        assertThat(salesPostProductRepository.findAll()).extracting(SalesPostProduct::getStatus).containsOnly(SalesPostProductStatus.DELETED);
        assertThat(outboxCount(PRODUCTS_DELETED_BY_POST, OutboxEventStatus.PUBLISHED)).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 판매자는 판매글을 삭제할 수 없다")
    void otherSellerCannotDeleteSalesPost() {
        assertThatThrownBy(() -> salesPostFacade.deleteSalesPostByUser(postId, OTHER_SELLER_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("POST_ACCESS_DENIED");

        assertThat(salesPostRepository.findById(postId).orElseThrow().getStatus()).isEqualTo(SalesPostSaleStatus.ON_SALE);
        assertThat(productRepository.findAll()).extracting(Product::getStatus).containsOnly(ProductStatus.ON_SALE);
        assertThat(outboxCount(SALES_POST_DELETED, null)).isZero();
    }

    // 삭제가 진행 중인 상황. 다시 삭제를 시작하지 못하고 상품도 건드리지 않아야 한다
    @Test
    @DisplayName("삭제 중(DELETING)인 판매글은 다시 삭제할 수 없다")
    void cannotDeleteWhileDeleting() {
        transactionTemplate.executeWithoutResult(status ->
                salesPostRepository.startDelete(postId, SalesPostUpdateStatus.editableStatuses()));

        assertThatThrownBy(() -> salesPostFacade.deleteSalesPostByUser(postId, SELLER_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("POST_SALE_PENDING_STATUS");

        assertThat(salesPostRepository.findById(postId).orElseThrow().getStatus()).isEqualTo(SalesPostSaleStatus.DELETING);
        assertThat(productRepository.findAll()).extracting(Product::getStatus).containsOnly(ProductStatus.ON_SALE);
    }

    private void relay() {
        ManualOutboxRelayConfig.releaseRelayLock(jdbcTemplate);
        outboxRelay.relay();
    }

    // status가 null이면 상태와 관계없이 센다
    private long outboxCount(String eventType, OutboxEventStatus status) {
        return queryFactory.select(outboxEvent.count())
                .from(outboxEvent)
                .where(outboxEvent.eventType.eq(eventType),
                        status == null ? null : outboxEvent.status.eq(status))
                .fetchOne();
    }

    // 상품 2개, 각각 사진 1장 · 옵션 1개
    private CreateSalesPostRequest request() {
        CreateSalesPostProduct iphone = new CreateSalesPostProduct(null, null, 1L, "아이폰", "설명", 10000L,
                List.of(new CreateSalesPostProductImage(null, null, null, 0, null)),
                List.of(inventory(new CreateProductOption(CLOTHING_SIZE, SIZE_L))));
        CreateSalesPostProduct charger = new CreateSalesPostProduct(null, null, 1L, "충전기", "설명", 3000L,
                List.of(new CreateSalesPostProductImage(null, null, null, 0, null)),
                List.of(inventory(new CreateProductOption(COLOR, BLACK))));

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
