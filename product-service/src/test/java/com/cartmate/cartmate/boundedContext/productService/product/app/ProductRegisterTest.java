package com.cartmate.cartmate.boundedContext.productService.product.app;

import com.cartmate.cartmate.boundedContext.productService.post.app.SalesPostCreateUseCase;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostProduct;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostProductImage;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostProductOption;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateSalesPostRequest;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductImageRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductOptionRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostRepository;
import com.cartmate.cartmate.boundedContext.productService.product.domain.Product;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductImage;
import com.cartmate.cartmate.boundedContext.productService.product.dto.CreateProduct;
import com.cartmate.cartmate.boundedContext.productService.product.dto.CreateProductImage;
import com.cartmate.cartmate.boundedContext.productService.product.dto.CreateProductOption;
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

    // 등록 이벤트(product dto) → post 리스너가 post dto로 바꿔 복제본 저장 → 동기화 완료 이벤트(post dto) → product가 product dto로 바꿔 동기화 표시
    @Test
    @DisplayName("동기화: 등록이 커밋되면 post에 같은 값의 상품 복제본이 생기고 product의 동기화 상태가 켜진다")
    void syncToPostAfterRegister() {
        SalesPost post = salesPostCreateUseCase.saveSalesPost(
                new CreateSalesPostRequest(null, SELLER_ID, "아이폰 팝니다", "상태 좋아요", List.of()));
        List<CreateProductOption> options = List.of(new CreateProductOption(1L, 100L), new CreateProductOption(2L, 200L));
        CreateProductRequest request = request(post.getId(),
                getDummyProduct(post.getId(), "아이폰", 2, options)
        );
        List<MultipartFile> imageList = getDummyImages(
                TestImageFiles.jpeg(),
                TestImageFiles.png()
        );

        productFacade.registerProduct(request, imageList);

        Product product = productRepository.findAll().getFirst();
        List<ProductImage> productImages = productImageRepository.findAll();

        List<SalesPostProduct> copies = salesPostProductRepository.findAll();
        assertThat(copies).hasSize(1);
        SalesPostProduct copy = copies.getFirst();
        assertThat(copy.getId()).isEqualTo(product.getId());
        assertThat(copy.getSellerId()).isEqualTo(SELLER_ID);
        assertThat(copy.getCategoryId()).isEqualTo(product.getCategoryId());
        assertThat(copy.getName()).isEqualTo("아이폰");
        assertThat(copy.getDescription()).isEqualTo(product.getDescription());
        assertThat(copy.getBasePrice()).isEqualTo(product.getBasePrice());

        assertThat(salesPostProductImageRepository.findAll())
                .extracting(SalesPostProductImage::getId, SalesPostProductImage::getS3Key,
                        SalesPostProductImage::getImageSeq, SalesPostProductImage::getStatus)
                .containsExactlyInAnyOrderElementsOf(productImages.stream()
                        .map(i -> tuple(i.getId(), i.getS3Key(), i.getImageSeq(), i.getStatus()))
                        .toList());
        assertThat(salesPostProductOptionRepository.findAll())
                .extracting(SalesPostProductOption::getProductTypeId, SalesPostProductOption::getProductOptionId)
                .containsExactlyInAnyOrder(tuple(1L, 100L), tuple(2L, 200L));

        assertThat(productRepository.findById(product.getId()).orElseThrow().isSyncStatus()).isTrue();
    }

    private CreateProductRequest request(Long postId, CreateProduct... products) {
        return new CreateProductRequest(postId, List.of(products));
    }

    private CreateProduct getDummyProduct(Long postId, String name, int imageCount, List<CreateProductOption> options) {
        List<CreateProductImage> images = new ArrayList<>();
        for (int seq = 0; seq < imageCount; seq++) {
            images.add(new CreateProductImage(null, SELLER_ID, null, null, seq, null));
        }
        return new CreateProduct(null, SELLER_ID, postId, 1L, name, "설명", 10000L, images, options);
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
