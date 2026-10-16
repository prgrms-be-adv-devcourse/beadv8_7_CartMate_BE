package com.cartmate.cartmate.boundedContext.product.app;

import com.cartmate.cartmate.boundedContext.product.domain.Product;
import com.cartmate.cartmate.boundedContext.product.domain.ProductImage;
import com.cartmate.cartmate.boundedContext.product.out.FakeImageStorage;
import com.cartmate.cartmate.boundedContext.product.out.ProductImageRepository;
import com.cartmate.cartmate.boundedContext.product.out.ProductRepository;
import com.cartmate.cartmate.global.exception.DomainException;
import com.cartmate.cartmate.shared.product.dto.create.CreateProduct;
import com.cartmate.cartmate.shared.product.dto.create.CreateProductImage;
import com.cartmate.cartmate.shared.product.dto.create.CreateProductRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@SpringBootTest
@ActiveProfiles("test")
class ProductFacadeIntegrationTest {

    private static final Long POST_ID = 101L;
    private static final Long SELLER_ID = 7L;

    @Autowired ProductFacade productFacade;
    @Autowired ProductRepository productRepository;
    @Autowired ProductImageRepository productImageRepository;
    @Autowired FakeImageStorage storage;

    @MockitoSpyBean
    ProductImageSaveUseCase productImageSaveUseCase;

    @BeforeEach
    void setUp() {
        productImageRepository.deleteAll();
        productRepository.deleteAll();
        storage.reset();
        reset(productImageSaveUseCase);
    }

    @Test
    @DisplayName("성공: 상품과 사진이 각자 맞는 상품 id로 저장된다")
    void registerSuccess() {
        // 상품 0: 사진 2장, 상품 1: 사진 1장
        CreateProductRequest request = request(
                product("아이폰", TestImageFiles.jpeg(), TestImageFiles.png()),
                product("충전기", TestImageFiles.jpeg()));

        productFacade.registerProduct(request);

        List<Product> products = productRepository.findAll();
        List<ProductImage> images = productImageRepository.findAll();

        assertThat(products).hasSize(2);
        assertThat(images).hasSize(3);
        assertThat(storage.savedCount()).isEqualTo(3);

        Long iphoneId = idOf(products, "아이폰");
        Long chargerId = idOf(products, "충전기");

        // 아이폰 사진 2장 (순서 0, 1), 충전기 사진 1장 (순서 0)
        assertThat(images).filteredOn(i -> Objects.equals(i.getProductId(), iphoneId))
                .extracting(ProductImage::getImageSeq)
                .containsExactlyInAnyOrder(0, 1);
        assertThat(images).filteredOn(i -> Objects.equals(i.getProductId(), chargerId))
                .extracting(ProductImage::getImageSeq)
                .containsExactly(0);

        // DB에 기록된 경로의 파일이 실제로 저장소에 있음
        assertThat(images).allMatch(i -> storage.exists(i.getS3Key()));
        // 모든 상품이 서버가 만든 글 번호에 속함
        assertThat(products).allMatch(p -> Objects.equals(p.getSalesPostId(), POST_ID));
    }

    @Test
    @DisplayName("검증 실패: 가짜 이미지가 섞이면 아무것도 저장되지 않는다")
    void validationFailure() {
        CreateProductRequest request = request(
                product("아이폰", TestImageFiles.jpeg()),
                product("충전기", TestImageFiles.fake()));

        assertThatThrownBy(() -> productFacade.registerProduct(request))
                .isInstanceOf(DomainException.class);

        assertThat(productRepository.count()).isZero();
        assertThat(productImageRepository.count()).isZero();
        assertThat(storage.savedCount()).isZero();
    }

    @Test
    @DisplayName("업로드 실패: 중간에 저장이 실패하면 파일과 DB 모두 비어 있다")
    void uploadFailure() {
        storage.failOnNthSave(2);   // 두 번째 파일 저장에서 실패
        CreateProductRequest request = request(
                product("아이폰", TestImageFiles.jpeg(), TestImageFiles.png()),
                product("충전기", TestImageFiles.jpeg()));

        assertThatThrownBy(() -> productFacade.registerProduct(request))
                .isInstanceOf(DomainException.class);

        assertThat(storage.savedCount()).isZero();
        assertThat(productRepository.count()).isZero();
        assertThat(productImageRepository.count()).isZero();
    }

    @Test
    @DisplayName("DB 저장 실패: 사진 저장이 실패하면 상품까지 함께 롤백된다")
    void dbSaveFailure() {
        doThrow(new RuntimeException("테스트용 DB 실패"))
                .when(productImageSaveUseCase).saveProductImage(any(), any());

        CreateProductRequest request = request(
                product("아이폰", TestImageFiles.jpeg()));

        assertThatThrownBy(() -> productFacade.registerProduct(request))
                .isInstanceOf(RuntimeException.class);

        assertThat(productRepository.count()).isZero();        // 상품도 롤백됨
        assertThat(productImageRepository.count()).isZero();
        // 파일 정리(deleteQuietly) 구현 후 아래 검증 추가
        // assertThat(storage.savedCount()).isZero();
    }

    // ===== 테스트 데이터 =====

    private CreateProductRequest request(CreateProduct... products) {
        return new CreateProductRequest(POST_ID, List.of(products));
    }

    private CreateProduct product(String name, MockMultipartFile... files) {
        List<CreateProductImage> images = new ArrayList<>();
        for (int seq = 0; seq < files.length; seq++) {
            images.add(new CreateProductImage(0L, SELLER_ID, 0L, null, seq, files[seq]));
        }
        // 실제 생성자에 맞게 수정
        return new CreateProduct(0L, 0L, POST_ID, 1L, name, "설명", 10000L, images);
    }

    private Long idOf(List<Product> products, String name) {
        return products.stream()
                .filter(p -> p.getName().equals(name))
                .findFirst()
                .orElseThrow()
                .getId();
    }
}