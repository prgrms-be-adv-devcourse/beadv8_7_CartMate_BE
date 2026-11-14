package com.cartmate.cartmate.boundedContext.productService.product.app;

import static com.cartmate.cartmate.support.TestInventories.inventory;
import static com.cartmate.cartmate.support.SeedOptions.*;
import com.cartmate.cartmate.boundedContext.productService.config.ProductServiceConsumers;
import com.cartmate.cartmate.boundedContext.productService.post.app.SalesPostCreateUseCase;
import com.cartmate.cartmate.boundedContext.productService.post.app.SalesPostSyncProductUseCase;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostProductImage;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostProductOption;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateSalesPostRequest;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductImageRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductOptionRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostRepository;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductImage;
import com.cartmate.cartmate.boundedContext.productService.product.domain.Inventory;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductOption;
import com.cartmate.cartmate.boundedContext.productService.product.dto.CreateProduct;
import com.cartmate.cartmate.boundedContext.productService.product.dto.CreateProductImage;
import com.cartmate.cartmate.boundedContext.productService.product.dto.CreateProductOption;
import com.cartmate.cartmate.boundedContext.productService.product.dto.CreateProductRequest;
import com.cartmate.cartmate.boundedContext.productService.product.dto.SyncProduct;
import com.cartmate.cartmate.boundedContext.productService.product.out.FakeImageStorage;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductImageRepository;
import com.cartmate.cartmate.boundedContext.productService.product.out.InventoryRepository;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductOptionRepository;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.outbox.ManualOutboxRelayConfig;
import com.cartmate.cartmate.global.outbox.OutboxEventStatus;
import com.cartmate.cartmate.global.outbox.OutboxRelay;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductStatus;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostProductStatus;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostSaleStatus;
import com.cartmate.cartmate.shared.post.event.SalesPostHiddenEvent;
import com.cartmate.cartmate.shared.post.event.SalesPostHideType;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static com.cartmate.cartmate.global.inbox.QProcessedEvent.processedEvent;
import static com.cartmate.cartmate.global.outbox.QOutboxEvent.outboxEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 상품 단건 삭제: product 상품 삭제 + ProductDeletedEvent(outbox) → post 복제본 삭제.
 *
 * <p>테스트는 스케줄링을 끄므로 outbox 릴레이를 직접 돌린다. Kafka는 꺼져 있어 같은 앱 안으로 전달된다.
 */
@SpringBootTest(properties = {
        "cartmate.scheduling.enabled=false",
        // ManualOutboxRelayConfig의 릴레이도 @Scheduled가 붙어 있어 저절로 돈다. 테스트가 직접 돌리도록 첫 실행을 미룬다(order-service와 같은 방식)
        "custom.outbox.relay.initial-delay=PT1H"
})
@ActiveProfiles("test")
@Import(ManualOutboxRelayConfig.class)
@RecordApplicationEvents
class ProductDeleteTest {

    private static final Long SELLER_ID = 7L;
    private static final Long OTHER_SELLER_ID = 8L;

    private static final String PRODUCT_DELETED = "ProductDeletedEvent";

    @Autowired
    ProductFacade productFacade;
    @Autowired
    ProductRepository productRepository;
    @Autowired
    ApplicationEvents events;
    @Autowired
    ProductImageRepository productImageRepository;
    @Autowired
    ProductOptionRepository productOptionRepository;
    @Autowired
    InventoryRepository inventoryRepository;
    @Autowired
    FakeImageStorage storage;

    @Autowired
    SalesPostCreateUseCase salesPostCreateUseCase;
    @Autowired
    SalesPostRepository salesPostRepository;
    @Autowired
    SalesPostProductRepository salesPostProductRepository;
    @Autowired
    SalesPostProductImageRepository salesPostProductImageRepository;
    @Autowired
    SalesPostProductOptionRepository salesPostProductOptionRepository;

    @Autowired
    OutboxRelay outboxRelay;
    @Autowired
    JdbcTemplate jdbcTemplate; // global의 릴레이 잠금 풀기(ManualOutboxRelayConfig)에만 넘긴다
    @Autowired
    JPAQueryFactory queryFactory;
    @Autowired
    TransactionTemplate transactionTemplate;
    @Autowired
    SalesPostSyncProductUseCase salesPostSyncProductUseCase;
    @Autowired
    ObjectMapper objectMapper;

    Long productId;      // 삭제할 상품 (아이폰: 사진 2, 옵션 2)
    Long otherProductId; // 같은 판매글에 남는 상품 (충전기: 사진 1, 옵션 1)

    // 판매글 + 상품 2개를 등록해서 post 복제본까지 만들어 둔다(마지막 상품은 지울 수 없으므로 2개)
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

        SalesPost post = salesPostCreateUseCase.saveSalesPost(
                new CreateSalesPostRequest(null, SELLER_ID, "아이폰 팝니다", "상태 좋아요", List.of()));
        CreateProduct iphone = new CreateProduct(null, SELLER_ID, post.getId(), 1L, "아이폰", "설명", 10000L,
                List.of(new CreateProductImage(null, SELLER_ID, null, null, 0, null),
                        new CreateProductImage(null, SELLER_ID, null, null, 1, null)),
                List.of(inventory(new CreateProductOption(CLOTHING_SIZE, SIZE_L), new CreateProductOption(COLOR, BLACK))));
        CreateProduct charger = new CreateProduct(null, SELLER_ID, post.getId(), 1L, "충전기", "설명", 3000L,
                List.of(new CreateProductImage(null, SELLER_ID, null, null, 0, null)),
                List.of(inventory(new CreateProductOption(SHOE_SIZE, SHOE_220))));
        List<SyncProduct> saved = productFacade.registerProduct(new CreateProductRequest(post.getId(), List.of(iphone, charger)),
                List.of(TestImageFiles.jpeg(), TestImageFiles.png(), TestImageFiles.jpeg()));
        createReplicas(saved);

        productId = productIdOf("아이폰");
        otherProductId = productIdOf("충전기");
    }

    // post가 상품 등록 응답으로 복제본을 만드는 것과 같게: 응답(product dto)을 JSON처럼 post dto로 바꿔 복제본을 저장한다
    private void createReplicas(List<SyncProduct> saved) {
        salesPostSyncProductUseCase.syncProduct(objectMapper.convertValue(saved,
                new TypeReference<List<com.cartmate.cartmate.boundedContext.productService.post.dto.SyncProduct>>() {}));
    }

    @Test
    @DisplayName("판매자가 상품을 삭제하면 상품이 먼저 지워지고, 이벤트가 전달되면 post 복제본도 삭제 상태가 된다")
    void deleteProductAlsoDeletesCopy() {
        productFacade.deleteProductByUser(productId, SELLER_ID);

        // 상품만 지워지고(사진 2장 · 재고 1개 · 선택된 옵션 2개 삭제 상태), 복제본 삭제는 이벤트로 남아 있다. 충전기는 그대로
        assertThat(productRepository.findById(productId).orElseThrow().getStatus()).isEqualTo(ProductStatus.DELETED);
        assertThat(productRepository.findById(otherProductId).orElseThrow().getStatus()).isEqualTo(ProductStatus.ON_SALE);
        assertThat(productImageRepository.findAll())
                .extracting(ProductImage::getStatus)
                .containsExactlyInAnyOrder(ProductStatus.DELETED, ProductStatus.DELETED, ProductStatus.ACTIVE);
        // 재고는 행이 남고 상태만 DELETED. 삭제하지 않은 상품(충전기)의 재고는 ACTIVE 그대로
        assertThat(inventoryRepository.findAll())
                .extracting(inventory -> inventory.getProduct().getId(), Inventory::getStatus)
                .containsExactlyInAnyOrder(
                        tuple(productId, ProductStatus.DELETED),
                        tuple(otherProductId, ProductStatus.ACTIVE));
        assertThat(productOptionRepository.findAll())
                .extracting(ProductOption::getStatus)
                .containsExactlyInAnyOrder(ProductStatus.DELETED, ProductStatus.DELETED, ProductStatus.ACTIVE);
        assertThat(salesPostProductRepository.findById(productId).orElseThrow().getStatus()).isEqualTo(SalesPostProductStatus.ON_SALE);
        assertThat(outboxCount(OutboxEventStatus.NEW)).isEqualTo(1);

        relay();

        assertThat(salesPostProductRepository.findById(productId).orElseThrow().getStatus()).isEqualTo(SalesPostProductStatus.DELETED);
        assertThat(salesPostProductRepository.findById(otherProductId).orElseThrow().getStatus()).isEqualTo(SalesPostProductStatus.ON_SALE);
        assertThat(salesPostProductImageRepository.findAll())
                .extracting(SalesPostProductImage::getStatus)
                .containsExactlyInAnyOrder(SalesPostProductStatus.DELETED, SalesPostProductStatus.DELETED, SalesPostProductStatus.ACTIVE);
        assertThat(salesPostProductOptionRepository.findAll())
                .extracting(SalesPostProductOption::getStatus)
                .containsExactlyInAnyOrder(SalesPostProductStatus.DELETED, SalesPostProductStatus.DELETED, SalesPostProductStatus.ACTIVE);
        assertThat(outboxCount(OutboxEventStatus.PUBLISHED)).isEqualTo(1);
    }

    @Test
    @DisplayName("판매글의 마지막 상품은 삭제할 수 없고, 판매글을 삭제하라고 안내한다")
    void cannotDeleteLastProduct() {
        productFacade.deleteProductByUser(productId, SELLER_ID);

        assertThatThrownBy(() -> productFacade.deleteProductByUser(otherProductId, SELLER_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("PRODUCT_DELETE_LAST")
                .hasMessageContaining("판매글을 삭제해 주세요");

        assertThat(productRepository.findById(otherProductId).orElseThrow().getStatus()).isEqualTo(ProductStatus.ON_SALE);
        assertThat(outboxCount(null)).isEqualTo(1); // 처음 지운 상품의 이벤트만
    }

    // 릴레이가 PUBLISHED 기록에 실패하면 같은 이벤트를 다시 보낸다. 복제본 삭제는 두 번 해도 상태가 같아서 처리 기록으로 확인한다
    @Test
    @DisplayName("같은 이벤트가 다시 와도 한 번만 처리된다")
    void sameEventIsProcessedOnce() {
        productFacade.deleteProductByUser(productId, SELLER_ID);
        relay();

        transactionTemplate.executeWithoutResult(status -> queryFactory.update(outboxEvent)
                .set(outboxEvent.status, OutboxEventStatus.NEW)
                .setNull(outboxEvent.publishedAt)
                .execute());
        relay();

        assertThat(queryFactory.select(processedEvent.count())
                .from(processedEvent)
                .where(processedEvent.consumer.eq(ProductServiceConsumers.PRODUCT_DELETED))
                .fetchOne()).isEqualTo(1);
        assertThat(salesPostProductRepository.findById(productId).orElseThrow().getStatus()).isEqualTo(SalesPostProductStatus.DELETED);
        assertThat(events.stream(SalesPostHiddenEvent.class)).hasSize(1); // 진행 중 거래 정리 알림도 한 번만
    }

    // 숨김 이벤트는 아직 앱 안 이벤트(EventPublisher)라 발행된 이벤트를 기록해서 확인한다 (outbox 전환은 다음 작업)
    @Test
    @DisplayName("상품 하나를 삭제하면 그 상품만 담은 판매글 숨김 이벤트(PRODUCT_DELETED)를 발행한다")
    void deleteProductPublishesHiddenEventForThatProduct() {
        SalesPostSaleStatus postStatusBefore = salesPostRepository.findAll().getFirst().getStatus();
        productFacade.deleteProductByUser(productId, SELLER_ID);
        assertThat(events.stream(SalesPostHiddenEvent.class)).isEmpty(); // 복제본을 지우는 처리에서 함께 나간다

        relay();

        List<SalesPostHiddenEvent> hidden = events.stream(SalesPostHiddenEvent.class).toList();
        assertThat(hidden).hasSize(1);
        SalesPostHiddenEvent event = hidden.getFirst();
        assertThat(event.productIds()).containsExactly(productId);
        assertThat(event.hideType()).isEqualTo(SalesPostHideType.PRODUCT_DELETED);
        assertThat(event.sellerId()).isEqualTo(SELLER_ID);
        // 판매글 상태는 바뀌지 않는다(상품 하나만 삭제)
        assertThat(salesPostRepository.findAll().getFirst().getStatus()).isEqualTo(postStatusBefore);
    }

    @Test
    @DisplayName("다른 판매자는 상품을 삭제할 수 없다")
    void otherSellerCannotDelete() {
        assertThatThrownBy(() -> productFacade.deleteProductByUser(productId, OTHER_SELLER_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("PRODUCT_ACCESS_DENIED");

        assertThat(productRepository.findById(productId).orElseThrow().getStatus()).isEqualTo(ProductStatus.ON_SALE);
        assertThat(salesPostProductRepository.findById(productId).orElseThrow().getStatus()).isEqualTo(SalesPostProductStatus.ON_SALE);
        assertThat(outboxCount(null)).isZero();
    }

    @Test
    @DisplayName("이미 삭제된 상품은 다시 삭제할 수 없다")
    void cannotDeleteTwice() {
        productFacade.deleteProductByUser(productId, SELLER_ID);

        assertThatThrownBy(() -> productFacade.deleteProductByUser(productId, SELLER_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("PRODUCT_NOT_FOUND");
    }

    private Long productIdOf(String name) {
        return productRepository.findAll().stream()
                .filter(p -> p.getName().equals(name))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    private void relay() {
        ManualOutboxRelayConfig.releaseRelayLock(jdbcTemplate);
        outboxRelay.relay();
    }

    // 상품 삭제 이벤트 수. status가 null이면 상태와 관계없이 센다
    private long outboxCount(OutboxEventStatus status) {
        return queryFactory.select(outboxEvent.count())
                .from(outboxEvent)
                .where(outboxEvent.eventType.eq(PRODUCT_DELETED),
                        status == null ? null : outboxEvent.status.eq(status))
                .fetchOne();
    }
}
