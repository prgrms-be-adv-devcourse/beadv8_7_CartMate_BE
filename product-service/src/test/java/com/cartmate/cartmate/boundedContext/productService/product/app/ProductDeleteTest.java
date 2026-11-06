package com.cartmate.cartmate.boundedContext.productService.product.app;

import com.cartmate.cartmate.boundedContext.productService.post.app.SalesPostCreateUseCase;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostProductImage;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateSalesPostRequest;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductImageRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductOptionRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostRepository;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductImage;
import com.cartmate.cartmate.boundedContext.productService.product.dto.CreateProduct;
import com.cartmate.cartmate.boundedContext.productService.product.dto.CreateProductImage;
import com.cartmate.cartmate.boundedContext.productService.product.dto.CreateProductOption;
import com.cartmate.cartmate.boundedContext.productService.product.dto.CreateProductRequest;
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

/**
 * 상품 단건 삭제: product에서 삭제 → ProductDeletedEvent(product dto) → post 리스너가 post dto로 바꿔 복제본 삭제.
 */
@SpringBootTest(properties = "cartmate.scheduling.enabled=false")
@ActiveProfiles("test")
class ProductDeleteTest {

    private static final Long SELLER_ID = 7L;
    private static final Long OTHER_SELLER_ID = 8L;

    @Autowired
    ProductFacade productFacade;
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

    Long productId;

    // 판매글 + 상품(사진 2, 옵션 2)을 등록해서 post 복제본까지 만들어 둔다
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
        CreateProduct product = new CreateProduct(null, SELLER_ID, post.getId(), 1L, "아이폰", "설명", 10000L,
                List.of(new CreateProductImage(null, SELLER_ID, null, null, 0, null),
                        new CreateProductImage(null, SELLER_ID, null, null, 1, null)),
                List.of(new CreateProductOption(1L, 100L), new CreateProductOption(2L, 200L)));
        productFacade.registerProduct(new CreateProductRequest(post.getId(), List.of(product)),
                List.of(TestImageFiles.jpeg(), TestImageFiles.png()));

        productId = productRepository.findAll().getFirst().getId();
    }

    @Test
    @DisplayName("판매자가 상품을 삭제하면 post 복제본도 삭제 상태가 된다")
    void deleteProductAlsoDeletesCopy() {
        productFacade.deleteProductByUser(productId, SELLER_ID);

        assertThat(productRepository.findById(productId).orElseThrow().getStatus()).isEqualTo(ProductStatus.DELETED);
        assertThat(productImageRepository.findAll())
                .hasSize(2)
                .extracting(ProductImage::getStatus)
                .containsOnly(ProductStatus.DELETED);
        assertThat(productOptionRepository.count()).isZero();

        assertThat(salesPostProductRepository.findById(productId).orElseThrow().getStatus()).isEqualTo(ProductStatus.DELETED);
        assertThat(salesPostProductImageRepository.findAll())
                .hasSize(2)
                .extracting(SalesPostProductImage::getStatus)
                .containsOnly(ProductStatus.DELETED);
        assertThat(salesPostProductOptionRepository.count()).isZero();
    }

    @Test
    @DisplayName("다른 판매자는 상품을 삭제할 수 없다")
    void otherSellerCannotDelete() {
        assertThatThrownBy(() -> productFacade.deleteProductByUser(productId, OTHER_SELLER_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("PRODUCT_ACCESS_DENIED");

        assertThat(productRepository.findById(productId).orElseThrow().getStatus()).isEqualTo(ProductStatus.ON_SALE);
        assertThat(salesPostProductRepository.findById(productId).orElseThrow().getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    @DisplayName("이미 삭제된 상품은 다시 삭제할 수 없다")
    void cannotDeleteTwice() {
        productFacade.deleteProductByUser(productId, SELLER_ID);

        assertThatThrownBy(() -> productFacade.deleteProductByUser(productId, SELLER_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("PRODUCT_NOT_FOUND");
    }
}
