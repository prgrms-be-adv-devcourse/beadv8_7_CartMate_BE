package com.cartmate.cartmate.boundedContext.productService.product.app;

import static com.cartmate.cartmate.support.TestInventories.inventory;
import static com.cartmate.cartmate.support.SeedOptions.*;
import com.cartmate.cartmate.support.TestInventories;
import com.cartmate.cartmate.boundedContext.productService.post.app.SalesPostCreateUseCase;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateSalesPostRequest;
import com.cartmate.cartmate.boundedContext.productService.product.dto.SyncProduct;
import com.cartmate.cartmate.boundedContext.productService.product.dto.SyncProductImage;
import com.cartmate.cartmate.boundedContext.productService.product.dto.SyncProductOption;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductImageRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductOptionRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostRepository;
import com.cartmate.cartmate.boundedContext.productService.product.domain.Product;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductImage;
import com.cartmate.cartmate.boundedContext.productService.product.dto.CreateProduct;
import com.cartmate.cartmate.boundedContext.productService.product.dto.CreateProductImage;
import com.cartmate.cartmate.boundedContext.productService.product.dto.CreateProductOption;
import com.cartmate.cartmate.boundedContext.productService.product.dto.CreateInventory;
import com.cartmate.cartmate.boundedContext.productService.product.dto.CreateProductRequest;
import com.cartmate.cartmate.boundedContext.productService.product.out.FakeImageStorage;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductImageRepository;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@SpringBootTest(properties = "cartmate.scheduling.enabled=false")
@ActiveProfiles("test")
class ProductRegisterTest {

    private static final Long POST_ID = 101L;
    private static final Long SELLER_ID = 7L;

    @Autowired
    ProductFacade productFacade;
    @Autowired
    ProductRepository productRepository;
    @Autowired
    ProductImageRepository productImageRepository;
    @Autowired
    FakeImageStorage storage;
    @MockitoSpyBean
    ProductCreateUseCase productCreateUseCase;

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

    @BeforeEach
    void setUp() {
        salesPostProductImageRepository.deleteAll();
        salesPostProductOptionRepository.deleteAll();
        salesPostProductRepository.deleteAll();
        salesPostRepository.deleteAll();
        productImageRepository.deleteAll();
        productRepository.deleteAll();
        storage.reset();
        reset(productCreateUseCase);
    }

    @Test
    @DisplayName("성공: 상품과 사진이 각자 맞는 상품 id로 저장된다")
    void registerSuccess() {
        CreateProductRequest request = request(POST_ID,
                getDummyProduct(POST_ID, "아이폰", 2, null),
                getDummyProduct(POST_ID, "충전기", 1, null)
        );
        List<MultipartFile> imageList = getDummyImages(
                TestImageFiles.jpeg(),
                TestImageFiles.png(),
                TestImageFiles.png()
        );

        productFacade.registerProduct(request, imageList);

        List<Product> products = productRepository.findAll();
        List<ProductImage> images = productImageRepository.findAll();

        assertThat(products).hasSize(2);
        assertThat(images).hasSize(3);
        assertThat(storage.savedCount()).isEqualTo(3);

        Long iphoneId = idOf(products, "아이폰");
        Long chargerId = idOf(products, "충전기");

        assertThat(images).filteredOn(i -> Objects.equals(i.getProduct().getId(), iphoneId))
                .extracting(ProductImage::getImageSeq)
                .containsExactlyInAnyOrder(0, 1);
        assertThat(images).filteredOn(i -> Objects.equals(i.getProduct().getId(), chargerId))
                .extracting(ProductImage::getImageSeq)
                .containsExactly(0);

        assertThat(images).allMatch(i -> storage.exists(i.getS3Key()));
        assertThat(products).allMatch(p -> Objects.equals(p.getSalesPostId(), POST_ID));
    }

    @Test
    @DisplayName("검증 실패: 가짜 이미지가 섞이면 아무것도 저장되지 않는다")
    void validationFailure() {
        CreateProductRequest request = request(POST_ID,
                getDummyProduct(POST_ID, "아이폰", 1, null),
                getDummyProduct(POST_ID, "충전기", 1, null)
        );
        List<MultipartFile> imageList = getDummyImages(
                TestImageFiles.jpeg(),
                TestImageFiles.fake()
        );

        assertThatThrownBy(() -> productFacade.registerProduct(request, imageList))
                .isInstanceOf(ServiceException.class);

        assertThat(productRepository.count()).isZero();
        assertThat(productImageRepository.count()).isZero();
        assertThat(storage.savedCount()).isZero();
    }

    @Test
    @DisplayName("카테고리: 상품이 카테고리를 참조해 저장되고, 없는 카테고리면 PRODUCT_CATEGORY_NOT_FOUND이고 아무것도 저장되지 않는다")
    void categoryIsLinked() {
        productFacade.registerProduct(request(POST_ID, getDummyProduct(POST_ID, "아이폰", 1, null)),
                getDummyImages(TestImageFiles.jpeg()));
        assertThat(productRepository.findAll()).extracting(Product::getCategoryId).containsExactly(1L);

        productRepository.deleteAll();
        productImageRepository.deleteAll();
        storage.reset();
        CreateProduct unknownCategory = new CreateProduct(null, SELLER_ID, POST_ID, 999L, "충전기", "설명", 10000L,
                List.of(new CreateProductImage(null, SELLER_ID, null, null, 0, null)),
                List.of(new CreateInventory(List.of(), TestInventories.ADDITIONAL_PRICE, TestInventories.STOCK_QUANTITY)));

        assertThatThrownBy(() -> productFacade.registerProduct(request(POST_ID, unknownCategory),
                getDummyImages(TestImageFiles.jpeg())))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("PRODUCT_CATEGORY_NOT_FOUND");
        assertThat(productRepository.count()).isZero();
    }

    @Test
    @DisplayName("검증 실패: 사진 항목보다 파일이 적거나 많으면 PRODUCT_CREATE_INVALID(400)이고 아무것도 저장되지 않는다")
    void imageCountMismatch() {
        CreateProductRequest request = request(POST_ID,
                getDummyProduct(POST_ID, "아이폰", 2, null)
        );

        assertThatThrownBy(() -> productFacade.registerProduct(request, getDummyImages(TestImageFiles.jpeg())))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("PRODUCT_CREATE_INVALID");
        assertThatThrownBy(() -> productFacade.registerProduct(request,
                getDummyImages(TestImageFiles.jpeg(), TestImageFiles.png(), TestImageFiles.png())))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("PRODUCT_CREATE_INVALID");

        assertThat(productRepository.count()).isZero();
        assertThat(productImageRepository.count()).isZero();
        assertThat(storage.savedCount()).isZero();
    }

    @Test
    @DisplayName("업로드 실패: 중간에 저장이 실패하면 파일과 DB 모두 비어 있다")
    void uploadFailure() {
        storage.failOnNthSave(2);
        CreateProductRequest request = request(POST_ID,
                getDummyProduct(POST_ID, "아이폰", 2, null),
                getDummyProduct(POST_ID, "충전기", 1, null)
        );
        List<MultipartFile> imageList = getDummyImages(
                TestImageFiles.jpeg(),
                TestImageFiles.png(),
                TestImageFiles.png()
        );

        assertThatThrownBy(() -> productFacade.registerProduct(request, imageList))
                .isInstanceOf(ServiceException.class);

        assertThat(storage.savedCount()).isZero();
        assertThat(productRepository.count()).isZero();
        assertThat(productImageRepository.count()).isZero();
    }

    @Test
    @DisplayName("DB 저장 실패: 상품 저장이 실패하면 DB와 파일 모두 비어 있다")
    void dbSaveFailure() {
        doThrow(new RuntimeException("테스트용 DB 실패"))
                .when(productCreateUseCase).saveProduct(any(), any());

        CreateProductRequest request = request(POST_ID,
                getDummyProduct(POST_ID, "아이폰", 1, null)
        );
        List<MultipartFile> imageList = getDummyImages(
                TestImageFiles.jpeg()
        );

        assertThatThrownBy(() -> productFacade.registerProduct(request, imageList))
                .isInstanceOf(RuntimeException.class);

        assertThat(productRepository.count()).isZero();
        assertThat(productImageRepository.count()).isZero();
        assertThat(storage.savedCount()).isZero();
    }

    // 복제본은 post가 이 응답으로 자기 마무리 트랜잭션에서 만든다(SalesPostRegisterFlowTest). product는 복제본을 만들지 않는다
    @Test
    @DisplayName("응답: 등록은 저장한 상품(사진 · 옵션 포함)을 그대로 돌려주고, post 복제본은 만들지 않는다")
    void returnSavedProducts() {
        SalesPost post = salesPostCreateUseCase.saveSalesPost(
                new CreateSalesPostRequest(null, SELLER_ID, "아이폰 팝니다", "상태 좋아요", List.of()));
        List<CreateInventory> options = List.of(inventory(new CreateProductOption(CLOTHING_SIZE, SIZE_L), new CreateProductOption(COLOR, BLACK)));
        CreateProductRequest request = request(post.getId(),
                getDummyProduct(post.getId(), "아이폰", 2, options)
        );
        List<MultipartFile> imageList = getDummyImages(
                TestImageFiles.jpeg(),
                TestImageFiles.png()
        );

        List<SyncProduct> saved = productFacade.registerProduct(request, imageList);

        Product product = productRepository.findAll().getFirst();
        List<ProductImage> productImages = productImageRepository.findAll();

        assertThat(saved).hasSize(1);
        SyncProduct returned = saved.getFirst();
        assertThat(returned.getId()).isEqualTo(product.getId());
        assertThat(returned.getSellerId()).isEqualTo(SELLER_ID);
        assertThat(returned.getSalesPostId()).isEqualTo(post.getId());
        assertThat(returned.getCategoryId()).isEqualTo(product.getCategoryId());
        assertThat(returned.getName()).isEqualTo("아이폰");
        assertThat(returned.getDescription()).isEqualTo(product.getDescription());
        assertThat(returned.getBasePrice()).isEqualTo(product.getBasePrice());

        assertThat(returned.getProductImageList())
                .extracting(SyncProductImage::getId, SyncProductImage::getS3Key,
                        SyncProductImage::getImageSeq, SyncProductImage::getStatus)
                .containsExactlyInAnyOrderElementsOf(productImages.stream()
                        .map(i -> tuple(i.getId(), i.getS3Key(), i.getImageSeq(), i.getStatus()))
                        .toList());
        assertThat(returned.getProductOptionList())
                .extracting(SyncProductOption::getProductTypeId, SyncProductOption::getProductOptionId)
                .containsExactlyInAnyOrder(tuple(CLOTHING_SIZE, SIZE_L), tuple(COLOR, BLACK));

        assertThat(salesPostProductRepository.count()).isZero();
    }

    private CreateProductRequest request(Long postId, CreateProduct... products) {
        return new CreateProductRequest(postId, List.of(products));
    }

    // inventories가 null이면 옵션 없는 기본 재고 하나로 등록한다(상품에는 재고가 하나 이상 있어야 한다)
    private CreateProduct getDummyProduct(Long postId, String name, int imageCount, List<CreateInventory> inventories) {
        List<CreateProductImage> images = new ArrayList<>();
        for (int seq = 0; seq < imageCount; seq++) {
            images.add(new CreateProductImage(null, SELLER_ID, null, null, seq, null));
        }
        List<CreateInventory> inventoryList = inventories != null ? inventories
                : List.of(new CreateInventory(List.of(), TestInventories.ADDITIONAL_PRICE, TestInventories.STOCK_QUANTITY));
        return new CreateProduct(null, SELLER_ID, postId, 1L, name, "설명", 10000L, images, inventoryList);
    }

    private List<MultipartFile> getDummyImages(MultipartFile... files) {
        return List.of(files);
    }

    private Long idOf(List<Product> products, String name) {
        return products.stream()
                .filter(p -> p.getName().equals(name))
                .findFirst()
                .orElseThrow()
                .getId();
    }
}
