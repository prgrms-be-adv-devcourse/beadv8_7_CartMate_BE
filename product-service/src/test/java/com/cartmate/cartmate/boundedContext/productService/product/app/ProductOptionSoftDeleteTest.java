package com.cartmate.cartmate.boundedContext.productService.product.app;

import com.cartmate.cartmate.boundedContext.productService.post.app.SalesPostCreateUseCase;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateSalesPostRequest;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductImageRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductOptionRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostRepository;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductOption;
import com.cartmate.cartmate.boundedContext.productService.product.dto.CreateProduct;
import com.cartmate.cartmate.boundedContext.productService.product.dto.CreateProductImage;
import com.cartmate.cartmate.boundedContext.productService.product.dto.CreateProductOption;
import com.cartmate.cartmate.boundedContext.productService.product.dto.CreateProductRequest;
import com.cartmate.cartmate.boundedContext.productService.product.dto.RestoreProduct;
import com.cartmate.cartmate.boundedContext.productService.product.dto.RestoreProductOption;
import com.cartmate.cartmate.boundedContext.productService.product.dto.RestoreProductsRequest;
import com.cartmate.cartmate.boundedContext.productService.product.dto.SelectProductOption;
import com.cartmate.cartmate.boundedContext.productService.product.dto.UpdateProduct;
import com.cartmate.cartmate.boundedContext.productService.product.dto.UpdateProductOption;
import com.cartmate.cartmate.boundedContext.productService.product.dto.UpdateProductsRequest;
import com.cartmate.cartmate.boundedContext.productService.product.out.FakeImageStorage;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductImageRepository;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductOptionRepository;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.product.ProductStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 상품 옵션 논리 삭제: 수정에서 빠진 옵션은 행을 지우지 않고 DELETED로 남기고(옵션 ID 유지),
 * 수정 실패 복구는 같은 값의 삭제된 옵션을 같은 ID로 되살린다. 조회에는 ACTIVE 옵션만 나온다.
 */
@SpringBootTest(properties = "cartmate.scheduling.enabled=false")
@ActiveProfiles("test")
class ProductOptionSoftDeleteTest {

    private static final Long SELLER_ID = 7L;

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
    Long keptOptionId;    // 종류 1 · 옵션 100
    Long removedOptionId; // 종류 2 · 옵션 200 (수정에서 뺄 옵션)

    // 판매글 + 상품(옵션 2개)을 등록해 둔다
    @BeforeEach
    void setUp() {
        salesPostProductImageRepository.deleteAll();
        salesPostProductOptionRepository.deleteAll();
        salesPostProductRepository.deleteAll();
        salesPostRepository.deleteAll();
        productImageRepository.deleteAll();
        productOptionRepository.deleteAll();
        productRepository.deleteAll();
        storage.reset();

        SalesPost post = salesPostCreateUseCase.saveSalesPost(
                new CreateSalesPostRequest(null, SELLER_ID, "아이폰 팝니다", "상태 좋아요", List.of()));
        postId = post.getId();
        CreateProduct product = new CreateProduct(null, SELLER_ID, postId, 1L, "아이폰", "설명", 10000L,
                List.of(new CreateProductImage(null, SELLER_ID, null, null, 0, null)),
                List.of(new CreateProductOption(1L, 100L), new CreateProductOption(2L, 200L)));
        productFacade.registerProduct(new CreateProductRequest(postId, List.of(product)), List.of(TestImageFiles.jpeg()));

        productId = productRepository.findAll().getFirst().getId();
        keptOptionId = optionIdOf(100L);
        removedOptionId = optionIdOf(200L);
    }

    @Test
    @DisplayName("수정에서 빠진 옵션은 행이 남고 DELETED가 되며, 남긴 옵션은 같은 ID로 ACTIVE다")
    void removedOptionIsSoftDeleted() {
        updateOptions(List.of(new UpdateProductOption(keptOptionId, 1L, 100L)));

        assertThat(productOptionRepository.findAll())
                .extracting(ProductOption::getId, ProductOption::getStatus)
                .containsExactlyInAnyOrder(
                        tuple(keptOptionId, ProductStatus.ACTIVE),
                        tuple(removedOptionId, ProductStatus.DELETED));
    }

    @Test
    @DisplayName("상품 조회에는 삭제된 옵션이 나오지 않는다")
    void deletedOptionIsHiddenFromQuery() {
        updateOptions(List.of(new UpdateProductOption(keptOptionId, 1L, 100L)));

        assertThat(productQueryUseCase.selectProductsByPost(postId).getFirst().getProductOptionList())
                .extracting(SelectProductOption::getId)
                .containsExactly(keptOptionId);
    }

    @Test
    @DisplayName("삭제된 옵션의 ID로는 수정할 수 없다")
    void cannotUpdateDeletedOption() {
        updateOptions(List.of(new UpdateProductOption(keptOptionId, 1L, 100L)));

        assertThatThrownBy(() -> updateOptions(List.of(
                new UpdateProductOption(keptOptionId, 1L, 100L),
                new UpdateProductOption(removedOptionId, 2L, 201L))))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("PRODUCT_OPTION_NOT_FOUND");
    }

    // 수정 실패 복구: 스냅샷(옵션 2개)으로 되돌릴 때 지운 옵션을 새로 만들지 않고 같은 행을 되살린다
    @Test
    @DisplayName("복구하면 수정 때 삭제된 옵션이 같은 ID로 되살아나고 새 행은 생기지 않는다")
    void restoreRevivesSameOption() {
        updateOptions(List.of(new UpdateProductOption(keptOptionId, 1L, 100L)));

        productRestoreUseCase.restoreProduct(postId, new RestoreProductsRequest(List.of(
                new RestoreProduct(productId, 1L, "아이폰", "설명", 10000L, null,
                        List.of(new RestoreProductOption(1L, 100L), new RestoreProductOption(2L, 200L))))));

        assertThat(productOptionRepository.findAll())
                .extracting(ProductOption::getId, ProductOption::getStatus)
                .containsExactlyInAnyOrder(
                        tuple(keptOptionId, ProductStatus.ACTIVE),
                        tuple(removedOptionId, ProductStatus.ACTIVE));
    }

    // 사진은 바꾸지 않고(null) 옵션만 바꾼다
    private void updateOptions(List<UpdateProductOption> options) {
        productUpdateUseCase.updateProduct(new UpdateProductsRequest(postId, List.of(
                new UpdateProduct(productId, 1L, "아이폰", "설명", 10000L, null, options))), List.of());
    }

    private Long optionIdOf(Long productOptionId) {
        return productOptionRepository.findAll().stream()
                .filter(o -> o.getProductOptionId().equals(productOptionId))
                .findFirst()
                .orElseThrow()
                .getId();
    }
}
