package com.cartmate.cartmate.boundedContext.productService.product.app;

import com.cartmate.cartmate.boundedContext.productService.post.app.SalesPostCreateUseCase;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateSalesPostRequest;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductImageRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductOptionRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostRepository;
import com.cartmate.cartmate.boundedContext.productService.product.domain.Inventory;
import com.cartmate.cartmate.boundedContext.productService.product.domain.InventoryReservation;
import com.cartmate.cartmate.boundedContext.productService.product.domain.InventoryReservationStatus;
import com.cartmate.cartmate.boundedContext.productService.product.domain.Product;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductStatus;
import com.cartmate.cartmate.boundedContext.productService.product.dto.CreateInventory;
import com.cartmate.cartmate.boundedContext.productService.product.dto.CreateProduct;
import com.cartmate.cartmate.boundedContext.productService.product.dto.CreateProductImage;
import com.cartmate.cartmate.boundedContext.productService.product.dto.CreateProductOption;
import com.cartmate.cartmate.boundedContext.productService.product.dto.CreateProductRequest;
import com.cartmate.cartmate.boundedContext.productService.product.dto.UpdateInventory;
import com.cartmate.cartmate.boundedContext.productService.product.dto.UpdateProduct;
import com.cartmate.cartmate.boundedContext.productService.product.dto.UpdateProductsRequest;
import com.cartmate.cartmate.boundedContext.productService.product.out.FakeImageStorage;
import com.cartmate.cartmate.boundedContext.productService.product.out.InventoryRepository;
import com.cartmate.cartmate.boundedContext.productService.product.out.InventoryReservationRepository;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductImageRepository;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductOptionRepository;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.order.OrderType;
import com.cartmate.cartmate.shared.product.dto.InventoryReleaseRequest;
import com.cartmate.cartmate.shared.product.dto.InventoryReserveRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.cartmate.cartmate.support.SeedOptions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 재고 선점 · 선점 해제: 결제 직전에 남은 수량(재고 - 선점)만큼만 잡고, 주문 하나는 전부 선점되거나 하나도 안 된다.
 * 선점 기록(주문 → 재고 · 수량)으로 선점 해제한다.
 */
@SpringBootTest(properties = "cartmate.scheduling.enabled=false")
@ActiveProfiles("test")
class InventoryReservationTest {

    private static final Long SELLER_ID = 7L;
    private static final OrderType ORDER = OrderType.PERSONAL;

    @Autowired
    ProductFacade productFacade;
    @Autowired
    ProductUpdateUseCase productUpdateUseCase;
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
    TransactionTemplate transactionTemplate;

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

    Long postId;
    Long productId;
    Long blackId;  // 재고 "L / 블랙" 10개
    Long whiteId;  // 재고 "L / 화이트" 5개

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

        SalesPost post = salesPostCreateUseCase.saveSalesPost(
                new CreateSalesPostRequest(null, SELLER_ID, "유니폼 팝니다", "새 상품", List.of()));
        CreateProduct product = new CreateProduct(null, SELLER_ID, post.getId(), 1L, "유니폼", "설명", 10000L,
                List.of(new CreateProductImage(null, SELLER_ID, null, null, 0, null)),
                List.of(inventory(10, SIZE_L, BLACK), inventory(5, SIZE_L, WHITE)));
        productFacade.registerProduct(new CreateProductRequest(post.getId(), List.of(product)), List.of(TestImageFiles.jpeg()));

        postId = post.getId();
        productId = productRepository.findAll().getFirst().getId();
        blackId = inventoryIdOf("L / 블랙");
        whiteId = inventoryIdOf("L / 화이트");
    }

    @Test
    @DisplayName("선점: 재고마다 선점 수량이 늘고, 주문 → 재고 · 판매자 · 수량 기록이 '선점'으로 남는다(재고 수량은 그대로)")
    void reserve() {
        productFacade.reserveInventories(reserveRequest(15L, item(blackId, 2), item(whiteId, 1)));

        assertThat(reservedOf(blackId)).isEqualTo(2);
        assertThat(reservedOf(whiteId)).isEqualTo(1);
        assertThat(stockOf(blackId)).isEqualTo(10);
        assertThat(inventoryReservationRepository.findByOrderTypeAndOrderIdOrderByInventoryIdAsc(ORDER, 15L))
                .extracting(InventoryReservation::getInventoryId, InventoryReservation::getSellerId,
                        InventoryReservation::getQuantity, InventoryReservation::getStatus)
                .containsExactly(
                        tuple(blackId, SELLER_ID, 2, InventoryReservationStatus.RESERVED),
                        tuple(whiteId, SELLER_ID, 1, InventoryReservationStatus.RESERVED));
    }

    @Test
    @DisplayName("선점: 같은 재고가 여러 번 오면 수량을 합쳐 한 줄로 선점한다")
    void reserveMergesSameInventory() {
        productFacade.reserveInventories(reserveRequest(15L, item(blackId, 2), item(blackId, 3)));

        assertThat(reservedOf(blackId)).isEqualTo(5);
        assertThat(inventoryReservationRepository.findByOrderTypeAndOrderIdOrderByInventoryIdAsc(ORDER, 15L))
                .extracting(InventoryReservation::getQuantity)
                .containsExactly(5);
    }

    @Test
    @DisplayName("선점: 하나라도 남은 수량이 부족하면 PRODUCT_INVENTORY_OUT_OF_STOCK이고 아무것도 선점되지 않는다")
    void reserveIsAllOrNothing() {
        assertThatThrownBy(() -> productFacade.reserveInventories(reserveRequest(15L, item(blackId, 2), item(whiteId, 6))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("PRODUCT_INVENTORY_OUT_OF_STOCK");

        assertThat(reservedOf(blackId)).isZero();
        assertThat(reservedOf(whiteId)).isZero();
        assertThat(inventoryReservationRepository.count()).isZero();
    }

    @Test
    @DisplayName("선점: 다른 주문이 잡은 만큼은 남은 수량에서 빠진다(재고 5개, 4개 선점 뒤 2개 요청은 부족)")
    void reserveCountsOtherReservations() {
        productFacade.reserveInventories(reserveRequest(15L, item(whiteId, 4)));

        assertThatThrownBy(() -> productFacade.reserveInventories(reserveRequest(16L, item(whiteId, 2))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("PRODUCT_INVENTORY_OUT_OF_STOCK");
        productFacade.reserveInventories(reserveRequest(17L, item(whiteId, 1)));

        assertThat(reservedOf(whiteId)).isEqualTo(5);
    }

    @Test
    @DisplayName("선점: 같은 주문을 다시 선점하면(결제 재시도) 두 번 잡지 않는다")
    void reserveSameOrderOnce() {
        productFacade.reserveInventories(reserveRequest(15L, item(blackId, 2)));
        productFacade.reserveInventories(reserveRequest(15L, item(blackId, 2)));

        assertThat(reservedOf(blackId)).isEqualTo(2);
        assertThat(inventoryReservationRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("선점: 상품이 판매 중이 아니면 PRODUCT_INVENTORY_NOT_PURCHASABLE, 없는 재고면 PRODUCT_INVENTORY_NOT_FOUND")
    void reserveRejectsNotPurchasable() {
        assertThatThrownBy(() -> productFacade.reserveInventories(reserveRequest(15L, item(999_999L, 1))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("PRODUCT_INVENTORY_NOT_FOUND");

        Product product = productRepository.findById(productId).orElseThrow();
        product.changeSaleStatusByPost(ProductStatus.PAUSED);
        productRepository.save(product);

        assertThatThrownBy(() -> productFacade.reserveInventories(reserveRequest(15L, item(blackId, 1))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("PRODUCT_INVENTORY_NOT_PURCHASABLE");
        assertThat(reservedOf(blackId)).isZero();
    }

    @Test
    @DisplayName("선점: 수량이 0 이하이거나 재고가 비어 있으면 PRODUCT_INVENTORY_RESERVE_INVALID")
    void reserveRejectsInvalidRequest() {
        assertThatThrownBy(() -> productFacade.reserveInventories(reserveRequest(15L, item(blackId, 0))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("PRODUCT_INVENTORY_RESERVE_INVALID");
        assertThatThrownBy(() -> productFacade.reserveInventories(new InventoryReserveRequest(ORDER, 15L, List.of())))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("PRODUCT_INVENTORY_RESERVE_INVALID");
    }

    @Test
    @DisplayName("선점 해제: 그 주문의 선점 수량만 줄고 기록이 '선점 해제'가 된다. 다시 해제해도 그대로, 다시 선점할 수 있다")
    void release() {
        productFacade.reserveInventories(reserveRequest(15L, item(blackId, 2), item(whiteId, 1)));
        productFacade.reserveInventories(reserveRequest(16L, item(blackId, 3)));

        productFacade.releaseInventories(new InventoryReleaseRequest(ORDER, 15L));
        productFacade.releaseInventories(new InventoryReleaseRequest(ORDER, 15L));

        assertThat(reservedOf(blackId)).isEqualTo(3);   // 주문 16의 선점만 남는다
        assertThat(reservedOf(whiteId)).isZero();
        assertThat(inventoryReservationRepository.findByOrderTypeAndOrderIdOrderByInventoryIdAsc(ORDER, 15L))
                .extracting(InventoryReservation::getStatus)
                .containsOnly(InventoryReservationStatus.RELEASED);

        productFacade.reserveInventories(reserveRequest(15L, item(blackId, 2)));   // 결제 실패 뒤 다시 결제
        assertThat(reservedOf(blackId)).isEqualTo(5);
    }

    @Test
    @DisplayName("선점 해제: 선점한 적 없는 주문이면 아무것도 하지 않는다")
    void releaseWithoutReservation() {
        productFacade.releaseInventories(new InventoryReleaseRequest(ORDER, 99L));

        assertThat(reservedOf(blackId)).isZero();
    }

    @Test
    @DisplayName("동시 선점: 재고 5개에 10개 주문이 1개씩 동시에 선점하면 5개만 성공한다(초과 판매 없음)")
    void concurrentReserveDoesNotOversell() throws Exception {
        int orders = 10;
        ExecutorService executor = Executors.newFixedThreadPool(orders);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (long orderId = 1; orderId <= orders; orderId++) {
            long id = orderId;
            futures.add(executor.submit(() -> {
                start.await();
                try {
                    productFacade.reserveInventories(reserveRequest(id, item(whiteId, 1)));
                    succeeded.incrementAndGet();
                } catch (ServiceException ignored) {
                    // 부족
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertThat(succeeded.get()).isEqualTo(5);
        assertThat(reservedOf(whiteId)).isEqualTo(5);
    }

    @Test
    @DisplayName("판매자 재고 수정이 선점과 겹쳐도 선점 수량을 옛 값으로 덮어쓰지 않는다(@DynamicUpdate)")
    void entityUpdateKeepsConcurrentReservation() throws Exception {
        CountDownLatch loaded = new CountDownLatch(1);
        CountDownLatch reserved = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        // 판매자 수정: 재고를 읽어 둔 뒤(선점 0) 다른 주문이 선점하고 나서 재고 수량만 바꿔 저장한다
        Future<?> sellerUpdate = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
            Inventory inventory = inventoryRepository.findById(blackId).orElseThrow();
            loaded.countDown();
            await(reserved);
            inventory.change(0L, 20);
        }));

        loaded.await(10, TimeUnit.SECONDS);
        productFacade.reserveInventories(reserveRequest(15L, item(blackId, 2)));
        reserved.countDown();
        sellerUpdate.get(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(stockOf(blackId)).isEqualTo(20);
        assertThat(reservedOf(blackId)).isEqualTo(2);
    }

    @Test
    @DisplayName("판매자 재고 수정: 선점된 수량보다 적게 바꾸면 PRODUCT_INVENTORY_BELOW_RESERVED이고 재고는 그대로다")
    void sellerCannotSetStockBelowReserved() {
        productFacade.reserveInventories(reserveRequest(15L, item(whiteId, 3)));

        assertThatThrownBy(() -> updateStocks(10, 2))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("PRODUCT_INVENTORY_BELOW_RESERVED");

        assertThat(stockOf(whiteId)).isEqualTo(5);
        assertThat(reservedOf(whiteId)).isEqualTo(3);
    }

    @Test
    @DisplayName("판매자 재고 수정: 선점된 수량과 같거나 많게는 바꿀 수 있다(선점은 그대로)")
    void sellerCanSetStockEqualToReserved() {
        productFacade.reserveInventories(reserveRequest(15L, item(whiteId, 3)));

        updateStocks(10, 3);

        assertThat(stockOf(whiteId)).isEqualTo(3);
        assertThat(reservedOf(whiteId)).isEqualTo(3);
    }

    // 판매글 수정의 상품 수정(재고 수량만 바꾼다. 사진은 그대로)
    private void updateStocks(int blackStock, int whiteStock) {
        productUpdateUseCase.updateProduct(new UpdateProductsRequest(postId, List.of(
                new UpdateProduct(productId, 1L, "유니폼", "설명", 10000L, null, List.of(
                        new UpdateInventory(blackId, null, 0L, blackStock),
                        new UpdateInventory(whiteId, null, 0L, whiteStock))))), List.of());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static InventoryReserveRequest reserveRequest(Long orderId, InventoryReserveRequest.Item... items) {
        return new InventoryReserveRequest(ORDER, orderId, List.of(items));
    }

    private static InventoryReserveRequest.Item item(Long inventoryId, int quantity) {
        return new InventoryReserveRequest.Item(inventoryId, quantity);
    }

    private static CreateInventory inventory(int stockQuantity, long sizeId, long colorId) {
        return new CreateInventory(List.of(
                new CreateProductOption(CLOTHING_SIZE, sizeId),
                new CreateProductOption(COLOR, colorId)), 0L, stockQuantity);
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
