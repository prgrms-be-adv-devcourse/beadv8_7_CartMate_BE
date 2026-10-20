package com.cartmate.cartmate.boundedContext.product.app;

import com.cartmate.cartmate.boundedContext.product.domain.Product;
import com.cartmate.cartmate.boundedContext.product.domain.ProductImage;
import com.cartmate.cartmate.boundedContext.product.out.FakeImageStorage;
import com.cartmate.cartmate.boundedContext.product.out.ProductImageRepository;
import com.cartmate.cartmate.boundedContext.product.out.ProductRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.post.dto.DeleteProductRequest;
import com.cartmate.cartmate.shared.post.event.PostDeletedEvent;
import com.cartmate.cartmate.shared.product.dto.CreateProduct;
import com.cartmate.cartmate.shared.product.dto.CreateProductImage;
import com.cartmate.cartmate.shared.product.dto.CreateProductRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@SpringBootTest
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
    ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        productImageRepository.deleteAll();
        productRepository.deleteAll();
        storage.reset();
        reset(productCreateUseCase);
    }

    @Test
    @DisplayName("성공: 상품과 사진이 각자 맞는 상품 id로 저장된다")
    void registerSuccess() {
        CreateProductRequest request = request(
                getDummyProduct("아이폰", 2),
                getDummyProduct("충전기", 1)
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
        CreateProductRequest request = request(
                getDummyProduct("아이폰", 1),
                getDummyProduct("충전기", 1)
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
        CreateProductRequest request = request(
                getDummyProduct("아이폰", 2),
                getDummyProduct("충전기", 1)
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

        CreateProductRequest request = request(
                getDummyProduct("아이폰", 1)
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

    private CreateProductRequest request(CreateProduct... products) {
        return new CreateProductRequest(POST_ID, List.of(products));
    }

    private CreateProduct getDummyProduct(String name, int imageCount) {
        List<CreateProductImage> images = new ArrayList<>();
        for (int seq = 0; seq < imageCount; seq++) {
            images.add(new CreateProductImage(null, SELLER_ID, null, null, seq, null));
        }
        return new CreateProduct(null, SELLER_ID, POST_ID, 1L, name, "설명", 10000L, images, null);
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