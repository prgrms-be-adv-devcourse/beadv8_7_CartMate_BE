package com.cartmate.cartmate.boundedContext.productService.product.app;

import com.cartmate.cartmate.boundedContext.productService.post.app.SalesPostCreateUseCase;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateSalesPostRequest;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductImageRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductOptionRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostRepository;
import com.cartmate.cartmate.boundedContext.productService.product.domain.InventoryReservation;
import com.cartmate.cartmate.boundedContext.productService.product.domain.InventoryReservationStatus;
import com.cartmate.cartmate.boundedContext.productService.product.dto.CreateInventory;
import com.cartmate.cartmate.boundedContext.productService.product.dto.CreateProduct;
import com.cartmate.cartmate.boundedContext.productService.product.dto.CreateProductImage;
import com.cartmate.cartmate.boundedContext.productService.product.dto.CreateProductOption;
import com.cartmate.cartmate.boundedContext.productService.product.dto.CreateProductRequest;
import com.cartmate.cartmate.boundedContext.productService.product.out.FakeImageStorage;
import com.cartmate.cartmate.boundedContext.productService.product.out.InventoryRepository;
import com.cartmate.cartmate.boundedContext.productService.product.out.InventoryReservationRepository;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductImageRepository;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductOptionRepository;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductRepository;
import com.cartmate.cartmate.shared.order.OrderCancelReason;
import com.cartmate.cartmate.shared.order.OrderType;
import com.cartmate.cartmate.shared.order.dto.DeliveryAddressInfo;
import com.cartmate.cartmate.shared.order.event.OrderCanceledEvent;
import com.cartmate.cartmate.shared.order.event.OrderPaidEvent;
import com.cartmate.cartmate.shared.order.event.OrderSellerItemsCanceledEvent;
import com.cartmate.cartmate.shared.product.dto.InventoryReserveRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static com.cartmate.cartmate.support.SeedOptions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 주문 이벤트 → 재고: 결제 완료 = 판매 확정, 주문 취소 = 선점 해제(결제 전) · 재고 복구(결제 후),
 * 판매자 몫 취소 = 그 판매자 재고만 재고 복구. 이벤트는 인박스처럼 트랜잭션 안에서 발행한다.
 */
@SpringBootTest(properties = "cartmate.scheduling.enabled=false")
@ActiveProfiles("test")
class InventoryReservationEventTest {

    private static final Long SELLER_ID = 7L;
    private static final Long OTHER_SELLER_ID = 8L;
    private static final OrderType ORDER = OrderType.PERSONAL;
    private static final Long ORDER_ID = 15L;

    @Autowired
    ProductFacade productFacade;
    @Autowired
    ApplicationEventPublisher eventPublisher;
    @Autowired
    TransactionTemplate transactionTemplate;
    @Autowired
    ProductRepository productRepository;
    @Autowired
    ProductImageRepository productImageRepository;
    @Autowired
    ProductOptionRepository productOptionRepository;
    @Autowired
    InventoryRepository inventoryRepository;
    @Autowired
    InventoryReservationRepository inventoryReservationRepository;
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

    Long blackId;  // 판매자 7, 재고 10개
    Long ballId;   // 판매자 8, 재고 20개

    @BeforeEach
    void setUp() {
        inventoryReservationRepository.deleteAll();
        salesPostProductImageRepository.deleteAll();
        salesPostProductOptionRepository.deleteAll();
        salesPostProductRepository.deleteAll();
        salesPostRepository.deleteAll();
        productImageRepository.deleteAll();
        productOptionRepository.deleteAll();
        inventoryRepository.deleteAll();
        productRepository.deleteAll();
        storage.reset();

        register(SELLER_ID, "유니폼", new CreateInventory(List.of(
                new CreateProductOption(CLOTHING_SIZE, SIZE_L), new CreateProductOption(COLOR, BLACK)), 0L, 10));
        register(OTHER_SELLER_ID, "축구공", new CreateInventory(List.of(
                new CreateProductOption(4L, 37L)), 0L, 20));   // 공 사이즈 5호
        blackId = inventoryIdOf("L / 블랙");
        ballId = inventoryIdOf("5호");

        productFacade.reserveInventories(new InventoryReserveRequest(ORDER, ORDER_ID, List.of(
                new InventoryReserveRequest.Item(blackId, 2),
                new InventoryReserveRequest.Item(ballId, 3))));
    }

    @Test
    @DisplayName("결제 완료 → 판매 확정: 재고와 선점에서 함께 빠지고(남은 수량 그대로) 기록이 '판매 확정'이 된다")
    void paidConfirms() {
        publish(paid());

        assertThat(stockOf(blackId)).isEqualTo(8);
        assertThat(reservedOf(blackId)).isZero();
        assertThat(stockOf(ballId)).isEqualTo(17);
        assertThat(statuses()).containsOnly(InventoryReservationStatus.CONFIRMED);
    }

    @Test
    @DisplayName("결제 완료가 두 번 와도 한 번만 판매 확정한다")
    void paidTwiceConfirmsOnce() {
        publish(paid());
        publish(paid());

        assertThat(stockOf(blackId)).isEqualTo(8);
        assertThat(reservedOf(blackId)).isZero();
    }

    @Test
    @DisplayName("결제 전 주문 취소(결제 기한 초과) → 선점 해제: 재고는 그대로, 선점만 0")
    void unpaidCancelReleases() {
        publish(canceled(false));

        assertThat(stockOf(blackId)).isEqualTo(10);
        assertThat(reservedOf(blackId)).isZero();
        assertThat(statuses()).containsOnly(InventoryReservationStatus.RELEASED);
    }

    @Test
    @DisplayName("결제 후 주문 취소 → 재고 복구: 판매 확정으로 빠진 재고가 돌아오고 기록이 '재고 복구'가 된다. 두 번 와도 한 번만")
    void paidCancelRestores() {
        publish(paid());
        publish(canceled(true));
        publish(canceled(true));

        assertThat(stockOf(blackId)).isEqualTo(10);
        assertThat(reservedOf(blackId)).isZero();
        assertThat(stockOf(ballId)).isEqualTo(20);
        assertThat(statuses()).containsOnly(InventoryReservationStatus.RESTORED);
    }

    @Test
    @DisplayName("순서 뒤바뀜: 취소가 결제 완료보다 먼저 오면 선점 해제되고, 늦게 온 결제 완료는 아무것도 하지 않는다")
    void cancelBeforePaid() {
        publish(canceled(true));
        publish(paid());

        assertThat(stockOf(blackId)).isEqualTo(10);
        assertThat(reservedOf(blackId)).isZero();
        assertThat(statuses()).containsOnly(InventoryReservationStatus.RELEASED);
    }

    @Test
    @DisplayName("판매자 몫 취소 → 그 판매자 재고만 재고 복구, 다른 판매자 재고는 판매 확정 그대로")
    void sellerItemsCanceledRestoresOnlyThatSeller() {
        publish(paid());
        publish(new OrderSellerItemsCanceledEvent(ORDER_ID, 1L, OTHER_SELLER_ID, 75000L, LocalDateTime.now()));

        assertThat(stockOf(ballId)).isEqualTo(20);
        assertThat(stockOf(blackId)).isEqualTo(8);
        assertThat(inventoryReservationRepository.findByOrderTypeAndOrderIdOrderByInventoryIdAsc(ORDER, ORDER_ID))
                .extracting(InventoryReservation::getInventoryId, InventoryReservation::getStatus)
                .containsExactlyInAnyOrder(
                        tuple(blackId, InventoryReservationStatus.CONFIRMED),
                        tuple(ballId, InventoryReservationStatus.RESTORED));
    }

    @Test
    @DisplayName("선점 기록이 없는 주문의 이벤트는 아무것도 바꾸지 않는다(기능 배포 전 주문 등)")
    void eventWithoutReservationDoesNothing() {
        publish(new OrderCanceledEvent(99L, 1L, ORDER, OrderCancelReason.BUYER_CANCELED, true, LocalDateTime.now(), 0L));

        assertThat(stockOf(blackId)).isEqualTo(10);
        assertThat(reservedOf(blackId)).isEqualTo(2);
    }

    // 인박스처럼 트랜잭션 안에서 발행한다(리스너는 커밋 직전에 실행)
    private void publish(Object event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));
    }

    private OrderPaidEvent paid() {
        return new OrderPaidEvent(ORDER_ID, "ORD-15", 1L, ORDER, null, LocalDateTime.now(),
                new DeliveryAddressInfo("구매자", "010-0000-0000", "12345", "주소", "상세"), List.of());
    }

    private OrderCanceledEvent canceled(boolean paid) {
        return new OrderCanceledEvent(ORDER_ID, 1L, ORDER,
                paid ? OrderCancelReason.BUYER_CANCELED : OrderCancelReason.PAYMENT_EXPIRED,
                paid, LocalDateTime.now(), paid ? 95000L : null);
    }

    private void register(Long sellerId, String name, CreateInventory inventory) {
        SalesPost post = salesPostCreateUseCase.saveSalesPost(
                new CreateSalesPostRequest(null, sellerId, name + " 팝니다", "새 상품", List.of()));
        CreateProduct product = new CreateProduct(null, sellerId, post.getId(), 1L, name, "설명", 25000L,
                List.of(new CreateProductImage(null, sellerId, null, null, 0, null)), List.of(inventory));
        productFacade.registerProduct(new CreateProductRequest(post.getId(), List.of(product)), List.of(TestImageFiles.jpeg()));
    }

    private List<InventoryReservationStatus> statuses() {
        return inventoryReservationRepository.findByOrderTypeAndOrderIdOrderByInventoryIdAsc(ORDER, ORDER_ID).stream()
                .map(InventoryReservation::getStatus)
                .toList();
    }

    private int reservedOf(Long inventoryId) {
        return inventoryRepository.findById(inventoryId).orElseThrow().getReservedQuantity();
    }

    private int stockOf(Long inventoryId) {
        return inventoryRepository.findById(inventoryId).orElseThrow().getStockQuantity();
    }

    private Long inventoryIdOf(String skuName) {
        return inventoryRepository.findAll().stream()
                .filter(i -> i.getSkuName().equals(skuName))
                .findFirst()
                .orElseThrow()
                .getId();
    }
}
