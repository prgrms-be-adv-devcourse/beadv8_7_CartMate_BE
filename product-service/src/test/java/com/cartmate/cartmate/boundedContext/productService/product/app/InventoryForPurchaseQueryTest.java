package com.cartmate.cartmate.boundedContext.productService.product.app;

import com.cartmate.cartmate.boundedContext.productService.post.app.SalesPostCreateUseCase;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateSalesPostRequest;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductImageRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductOptionRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostRepository;
import com.cartmate.cartmate.boundedContext.productService.product.domain.Product;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductStatus;
import com.cartmate.cartmate.boundedContext.productService.product.dto.CompleteProductUpdateRequest;
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
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductImageRepository;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductOptionRepository;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductRepository;
import com.cartmate.cartmate.shared.product.dto.InventoryForPurchase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static com.cartmate.cartmate.support.SeedOptions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 구매용 재고 조회(장바구니 · 주문이 부르는 내부 API의 조회 로직).
 * 옵션명은 재고 이름(skuName), 가격은 적용가(기본가 + 추가금액)이고,
 * 구매 가능 = 상품 판매 중 + 재고 삭제 안 됨 + 남은 수량(재고 - 예약) 1개 이상.
 */
@SpringBootTest(properties = "cartmate.scheduling.enabled=false")
@ActiveProfiles("test")
class InventoryForPurchaseQueryTest {

    private static final Long SELLER_ID = 7L;
    private static final Long CATEGORY_ID = 1L;
    private static final Long BASE_PRICE = 10000L;

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

    Long postId;
    Long productId;
    Long blackId;  // 재고 "L / 블랙" (추가금액 0, 10개)
    Long whiteId;  // 재고 "L / 화이트" (추가금액 1,000, 5개)

    @BeforeEach
    void setUp() {
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
        postId = post.getId();

        CreateProduct product = new CreateProduct(null, SELLER_ID, postId, CATEGORY_ID, "유니폼", "설명", BASE_PRICE,
                List.of(new CreateProductImage(null, SELLER_ID, null, null, 0, null)),
                List.of(inventory(0L, 10, SIZE_L, BLACK), inventory(1000L, 5, SIZE_L, WHITE)));
        productFacade.registerProduct(new CreateProductRequest(postId, List.of(product)), List.of(TestImageFiles.jpeg()));

        productId = productRepository.findAll().getFirst().getId();
        blackId = inventoryIdOf("L / 블랙");
        whiteId = inventoryIdOf("L / 화이트");
    }

    @Test
    @DisplayName("재고 ID 조회: 옵션명은 재고 이름, 가격은 적용가로 요청 순서대로 나오고, 없는 ID는 빠진다")
    void findByInventoryIds() {
        List<InventoryForPurchase> result = productFacade.findInventoriesForPurchase(List.of(whiteId, 999_999L, blackId));

        assertThat(result)
                .extracting(InventoryForPurchase::inventoryId, InventoryForPurchase::productId,
                        InventoryForPurchase::sellerId, InventoryForPurchase::productName,
                        InventoryForPurchase::optionName, InventoryForPurchase::price,
                        InventoryForPurchase::categoryId, InventoryForPurchase::purchasable)
                .containsExactly(
                        tuple(whiteId, productId, SELLER_ID, "유니폼", "L / 화이트", 11000L, CATEGORY_ID, true),
                        tuple(blackId, productId, SELLER_ID, "유니폼", "L / 블랙", 10000L, CATEGORY_ID, true));
        assertThat(result).allSatisfy(inventory -> assertThat(inventory.imageUrl()).isNotNull());
    }

    @Test
    @DisplayName("재고 ID 조회: 남은 수량이 0이면 구매 불가")
    void soldOutInventoryIsNotPurchasable() {
        updateInventories(List.of(
                new UpdateInventory(blackId, null, 0L, 0),
                new UpdateInventory(whiteId, null, 1000L, 5)));

        assertThat(productFacade.findInventoriesForPurchase(List.of(blackId, whiteId)))
                .extracting(InventoryForPurchase::inventoryId, InventoryForPurchase::purchasable)
                .containsExactly(tuple(blackId, false), tuple(whiteId, true));
    }

    @Test
    @DisplayName("재고 ID 조회: 상품이 판매 중이 아니면(판매 중지) 모든 재고가 구매 불가")
    void pausedProductIsNotPurchasable() {
        Product product = productRepository.findById(productId).orElseThrow();
        product.changeSaleStatusByPost(ProductStatus.PAUSED);
        productRepository.save(product);

        assertThat(productFacade.findInventoriesForPurchase(List.of(blackId, whiteId)))
                .extracting(InventoryForPurchase::purchasable)
                .containsExactly(false, false);
    }

    @Test
    @DisplayName("삭제된 재고: 재고 ID 조회에는 구매 불가로 나오고, 상품 ID 조회에서는 빠진다")
    void deletedInventory() {
        updateInventories(List.of(new UpdateInventory(blackId, null, 0L, 10)));   // 화이트를 뺀다 → 삭제

        assertThat(productFacade.findInventoriesForPurchase(List.of(whiteId)))
                .extracting(InventoryForPurchase::inventoryId, InventoryForPurchase::purchasable)
                .containsExactly(tuple(whiteId, false));
        assertThat(productFacade.findInventoriesForPurchaseByProducts(List.of(productId)))
                .extracting(InventoryForPurchase::inventoryId)
                .containsExactly(blackId);
    }

    @Test
    @DisplayName("상품 ID 조회: 그 상품의 살아 있는 재고가 재고 ID 순으로 나온다")
    void findByProductIds() {
        assertThat(productFacade.findInventoriesForPurchaseByProducts(List.of(productId, 999_999L)))
                .extracting(InventoryForPurchase::inventoryId, InventoryForPurchase::optionName,
                        InventoryForPurchase::price, InventoryForPurchase::purchasable)
                .containsExactly(
                        tuple(blackId, "L / 블랙", 10000L, true),
                        tuple(whiteId, "L / 화이트", 11000L, true));
    }

    @Test
    @DisplayName("빈 요청이면 조회하지 않고 빈 목록")
    void emptyRequest() {
        assertThat(productFacade.findInventoriesForPurchase(List.of())).isEmpty();
        assertThat(productFacade.findInventoriesForPurchaseByProducts(List.of())).isEmpty();
    }

    // 실제 흐름처럼 수정(→ UPDATING) 후 수정 완료(→ 수정 전 상태 ON_SALE)까지 한다. 사진은 바꾸지 않는다(null)
    private void updateInventories(List<UpdateInventory> inventories) {
        productUpdateUseCase.updateProduct(new UpdateProductsRequest(postId, List.of(
                new UpdateProduct(productId, CATEGORY_ID, "유니폼", "설명", BASE_PRICE, null, inventories))), List.of());
        productUpdateUseCase.completeUpdate(postId, new CompleteProductUpdateRequest(List.of(
                new CompleteProductUpdateRequest.Item(productId, ProductStatus.ON_SALE))));
    }

    private static CreateInventory inventory(Long additionalPrice, int stockQuantity, long sizeId, long colorId) {
        return new CreateInventory(List.of(
                new CreateProductOption(CLOTHING_SIZE, sizeId),
                new CreateProductOption(COLOR, colorId)), additionalPrice, stockQuantity);
    }

    private Long inventoryIdOf(String skuName) {
        return inventoryRepository.findAll().stream()
                .filter(i -> i.getSkuName().equals(skuName))
                .findFirst()
                .orElseThrow()
                .getId();
    }
}
