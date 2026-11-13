package com.cartmate.cartmate.boundedContext.productService.product.app;

import com.cartmate.cartmate.boundedContext.productService.post.app.SalesPostCreateUseCase;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateSalesPostRequest;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductImageRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductOptionRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostRepository;
import com.cartmate.cartmate.boundedContext.productService.product.domain.Inventory;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductOption;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductStatus;
import com.cartmate.cartmate.boundedContext.productService.product.dto.CreateInventory;
import com.cartmate.cartmate.boundedContext.productService.product.dto.CreateProduct;
import com.cartmate.cartmate.boundedContext.productService.product.dto.CreateProductImage;
import com.cartmate.cartmate.boundedContext.productService.product.dto.CreateProductOption;
import com.cartmate.cartmate.boundedContext.productService.product.dto.CreateProductRequest;
import com.cartmate.cartmate.boundedContext.productService.product.dto.RestoreInventory;
import com.cartmate.cartmate.boundedContext.productService.product.dto.RestoreProduct;
import com.cartmate.cartmate.boundedContext.productService.product.dto.RestoreProductsRequest;
import com.cartmate.cartmate.boundedContext.productService.product.dto.SelectInventory;
import com.cartmate.cartmate.boundedContext.productService.product.dto.UpdateInventory;
import com.cartmate.cartmate.boundedContext.productService.product.dto.UpdateProduct;
import com.cartmate.cartmate.boundedContext.productService.product.dto.UpdateProductsRequest;
import com.cartmate.cartmate.boundedContext.productService.product.out.FakeImageStorage;
import com.cartmate.cartmate.boundedContext.productService.product.out.InventoryRepository;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductImageRepository;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductOptionRepository;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static com.cartmate.cartmate.support.SeedOptions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 재고 흐름: 등록은 상품정보 → 이미지 → 재고 → 재고에서 분리된 선택된 옵션 순으로 저장한다.
 * 수정에서 빠진 재고는 선택된 옵션과 함께 DELETED로 남고(재고 ID 유지), 수정 실패 복구는 같은 재고를 같은 ID로 되살린다.
 */
@SpringBootTest(properties = "cartmate.scheduling.enabled=false")
@ActiveProfiles("test")
class InventoryFlowTest {

    private static final Long SELLER_ID = 7L;
    private static final Long BASE_PRICE = 10000L;

    @Autowired
    ProductFacade productFacade;
    @Autowired
    ProductUpdateUseCase productUpdateUseCase;
    @Autowired
    ProductRestoreUseCase productRestoreUseCase;
    @Autowired
    ProductQueryUseCase productQueryUseCase;
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
    Long whiteId;  // 재고 "L / 화이트" (추가금액 1,000, 5개) — 수정에서 뺄 재고

    // 판매글 + 상품(재고 2개)을 등록해 둔다
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
        register(List.of(
                inventory(0L, 10, SIZE_L, BLACK),
                inventory(1000L, 5, SIZE_L, WHITE)));

        productId = productRepository.findAll().getFirst().getId();
        blackId = inventoryIdOf("L / 블랙");
        whiteId = inventoryIdOf("L / 화이트");
    }

    @Test
    @DisplayName("등록: 재고마다 이름 · 최종 가격(기본가 + 추가금액)이 저장되고, 재고에서 분리된 선택된 옵션이 재고를 가리킨다")
    void registerSavesInventoriesAndSplitOptions() {
        assertThat(inventoryRepository.findAll())
                .extracting(Inventory::getSkuName, Inventory::getAdditionalPrice, Inventory::getAppliedPrice,
                        Inventory::getStockQuantity, Inventory::getReservedQuantity, Inventory::getStatus)
                .containsExactlyInAnyOrder(
                        tuple("L / 블랙", 0L, 10000L, 10, 0, ProductStatus.ACTIVE),
                        tuple("L / 화이트", 1000L, 11000L, 5, 0, ProductStatus.ACTIVE));

        assertThat(productOptionRepository.findAll())
                .extracting(o -> o.getInventory().getId(), ProductOption::getProductTypeId, ProductOption::getProductOptionId)
                .containsExactlyInAnyOrder(
                        tuple(blackId, CLOTHING_SIZE, SIZE_L), tuple(blackId, COLOR, BLACK),
                        tuple(whiteId, CLOTHING_SIZE, SIZE_L), tuple(whiteId, COLOR, WHITE));
    }

    @Test
    @DisplayName("등록: 재고가 없으면 거부한다")
    void registerRequiresInventory() {
        assertThatThrownBy(() -> register(List.of()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("PRODUCT_INVENTORY_REQUIRED");
    }

    @Test
    @DisplayName("등록: 같은 상품에 같은 옵션 조합의 재고가 두 번 오면 거부한다")
    void registerRejectsDuplicateCombination() {
        assertThatThrownBy(() -> register(List.of(inventory(0L, 1, SIZE_M, BLACK), inventory(0L, 2, BLACK, SIZE_M))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("PRODUCT_INVENTORY_DUPLICATE_COMBINATION");
    }

    @Test
    @DisplayName("등록: 한 재고에 같은 옵션 종류가 두 번 들어가면 거부한다")
    void registerRejectsDuplicateOptionType() {
        assertThatThrownBy(() -> register(List.of(inventory(0L, 1, SIZE_M, SIZE_L))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("PRODUCT_INVENTORY_DUPLICATE_OPTION_TYPE");
    }

    @Test
    @DisplayName("수정: 빠진 재고는 선택된 옵션과 함께 DELETED, 남긴 재고는 값만 바뀌고, 새 재고는 추가된다")
    void updateDiffsInventories() {
        update(BASE_PRICE, List.of(
                new UpdateInventory(blackId, null, 500L, 3),
                new UpdateInventory(null, List.of(option(CLOTHING_SIZE, SIZE_M), option(COLOR, BLACK)), 0L, 7)));

        assertThat(inventoryRepository.findAll())
                .extracting(Inventory::getSkuName, Inventory::getAppliedPrice, Inventory::getStockQuantity, Inventory::getStatus)
                .containsExactlyInAnyOrder(
                        tuple("L / 블랙", 10500L, 3, ProductStatus.ACTIVE),
                        tuple("L / 화이트", 11000L, 5, ProductStatus.DELETED),
                        tuple("M / 블랙", 10000L, 7, ProductStatus.ACTIVE));
        assertThat(productOptionRepository.findAll())
                .filteredOn(o -> o.getInventory().getId().equals(whiteId))
                .extracting(ProductOption::getStatus)
                .containsOnly(ProductStatus.DELETED);

        // 조회에는 살아 있는 재고만 나온다
        assertThat(productQueryUseCase.selectProductsByPost(postId).getFirst().getInventoryList())
                .extracting(SelectInventory::getSkuName)
                .containsExactlyInAnyOrder("L / 블랙", "M / 블랙");
    }

    @Test
    @DisplayName("수정: 기본가가 바뀌면 재고의 최종 가격도 다시 계산된다")
    void basePriceChangeRecalculatesAppliedPrice() {
        update(20000L, null);

        assertThat(inventoryRepository.findAll())
                .extracting(Inventory::getSkuName, Inventory::getAppliedPrice)
                .containsExactlyInAnyOrder(tuple("L / 블랙", 20000L), tuple("L / 화이트", 21000L));
    }

    @Test
    @DisplayName("수정: 삭제된 재고의 ID로는 수정할 수 없다")
    void cannotUpdateDeletedInventory() {
        update(BASE_PRICE, List.of(new UpdateInventory(blackId, null, 0L, 10)));

        assertThatThrownBy(() -> update(BASE_PRICE, List.of(
                new UpdateInventory(blackId, null, 0L, 10),
                new UpdateInventory(whiteId, null, 1000L, 5))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("PRODUCT_INVENTORY_NOT_FOUND");
    }

    @Test
    @DisplayName("수정: 재고를 모두 빼면 거부한다")
    void updateRequiresInventory() {
        assertThatThrownBy(() -> update(BASE_PRICE, List.of()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("PRODUCT_INVENTORY_REQUIRED");
    }

    // 수정 실패 복구: 스냅샷(재고 2개)으로 되돌릴 때 지운 재고를 새로 만들지 않고 같은 행을 되살린다
    @Test
    @DisplayName("복구: 수정 때 지운 재고가 같은 ID로 되살아나고, 수정에서 추가된 재고는 삭제된다")
    void restoreRevivesSameInventory() {
        update(BASE_PRICE, List.of(
                new UpdateInventory(blackId, null, 500L, 3),
                new UpdateInventory(null, List.of(option(CLOTHING_SIZE, SIZE_M), option(COLOR, BLACK)), 0L, 7)));

        productRestoreUseCase.restoreProduct(postId, new RestoreProductsRequest(List.of(
                new RestoreProduct(productId, 1L, "유니폼", "설명", BASE_PRICE, null, List.of(
                        new RestoreInventory(blackId, 0L, 10),
                        new RestoreInventory(whiteId, 1000L, 5))))));

        assertThat(inventoryRepository.findAll())
                .extracting(Inventory::getId, Inventory::getAppliedPrice, Inventory::getStockQuantity, Inventory::getStatus)
                .containsExactlyInAnyOrder(
                        tuple(blackId, 10000L, 10, ProductStatus.ACTIVE),
                        tuple(whiteId, 11000L, 5, ProductStatus.ACTIVE),
                        tuple(inventoryIdOf("M / 블랙"), 10000L, 7, ProductStatus.DELETED));
        assertThat(productOptionRepository.findAll())
                .filteredOn(o -> o.getInventory().getId().equals(whiteId))
                .extracting(ProductOption::getStatus)
                .containsOnly(ProductStatus.ACTIVE);
    }

    private void register(List<CreateInventory> inventories) {
        CreateProduct product = new CreateProduct(null, SELLER_ID, postId, 1L, "유니폼", "설명", BASE_PRICE,
                List.of(new CreateProductImage(null, SELLER_ID, null, null, 0, null)), inventories);
        productFacade.registerProduct(new CreateProductRequest(postId, List.of(product)), List.of(TestImageFiles.jpeg()));
    }

    // 사진은 바꾸지 않는다(null). inventories가 null이면 재고도 바꾸지 않는다
    private void update(Long basePrice, List<UpdateInventory> inventories) {
        productUpdateUseCase.updateProduct(new UpdateProductsRequest(postId, List.of(
                new UpdateProduct(productId, 1L, "유니폼", "설명", basePrice, null, inventories))), List.of());
    }

    // 상품옵션 ID로 옵션 종류를 채운 재고 요청 (상품옵션 순서대로 재고 이름이 정해진다)
    private static CreateInventory inventory(Long additionalPrice, int stockQuantity, long... optionValueIds) {
        List<CreateProductOption> options = new java.util.ArrayList<>();
        for (long id : optionValueIds) {
            options.add(option(typeOf(id), id));
        }
        return new CreateInventory(options, additionalPrice, stockQuantity);
    }

    private static CreateProductOption option(long typeId, long optionValueId) {
        return new CreateProductOption(typeId, optionValueId);
    }

    private static long typeOf(long optionValueId) {
        return optionValueId <= SIZE_L + 3 ? CLOTHING_SIZE : COLOR;   // 시드: 1~7 의류 사이즈, 8~17 색상
    }

    private Long inventoryIdOf(String skuName) {
        return inventoryRepository.findAll().stream()
                .filter(i -> i.getSkuName().equals(skuName))
                .findFirst()
                .orElseThrow()
                .getId();
    }
}
