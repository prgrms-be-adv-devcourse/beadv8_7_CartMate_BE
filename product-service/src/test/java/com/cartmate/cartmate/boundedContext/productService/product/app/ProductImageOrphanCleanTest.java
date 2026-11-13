package com.cartmate.cartmate.boundedContext.productService.product.app;

import static com.cartmate.cartmate.support.SeedOptions.*;
import com.cartmate.cartmate.boundedContext.productService.post.app.SalesPostCreateUseCase;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPost;
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
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductStatus;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static com.cartmate.cartmate.boundedContext.productService.product.domain.QProductImage.productImage;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 고아 사진 파일 정리: 저장소에는 있는데 DB(사진 행)에는 키가 없고, 올린 지 하루가 지난 파일만 지운다.
 *
 * <p>정상 사진은 실제 상품 등록으로 만들고(파일 + DB 행), 고아 파일은 저장소에만 직접 넣는다(FakeImageStorage.putFile).
 * 기준 시각(uploadedBefore)은 스케줄러처럼 "지금 - 1일"을 넘긴다.
 */
@SpringBootTest(properties = "cartmate.scheduling.enabled=false")
@ActiveProfiles("test")
class ProductImageOrphanCleanTest {

    private static final Long SELLER_ID = 7L;
    private static final Duration GRACE = Duration.ofDays(1);

    @Autowired
    ProductImageOrphanCleanUseCase productImageOrphanCleanUseCase;
    @Autowired
    ProductFacade productFacade;
    @Autowired
    SalesPostCreateUseCase salesPostCreateUseCase;
    @Autowired
    FakeImageStorage storage;
    @Autowired
    JPAQueryFactory queryFactory;
    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    ProductRepository productRepository;
    @Autowired
    ProductImageRepository productImageRepository;
    @Autowired
    ProductOptionRepository productOptionRepository;
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
        productOptionRepository.deleteAll();
        productRepository.deleteAll();
        storage.reset();
    }

    @Test
    @DisplayName("DB에 없고 올린 지 하루가 지난 파일은 지운다")
    void deleteOldOrphan() {
        storage.putFile("posts/1/orphan.jpg", Instant.now().minus(Duration.ofDays(2)));

        int orphanCount = cleanUp(false);

        assertThat(orphanCount).isEqualTo(1);
        assertThat(storage.exists("posts/1/orphan.jpg")).isFalse();
    }

    // 업로드는 "파일 저장 → DB 저장" 순서라, 그 사이의 정상 파일이 고아처럼 보인다
    @Test
    @DisplayName("DB에 없어도 올린 지 하루가 안 된 파일은 업로드 중일 수 있어 그대로 둔다")
    void keepRecentOrphan() {
        storage.putFile("posts/1/uploading.jpg", Instant.now().minus(Duration.ofMinutes(10)));

        int orphanCount = cleanUp(false);

        assertThat(orphanCount).isZero();
        assertThat(storage.exists("posts/1/uploading.jpg")).isTrue();
    }

    @Test
    @DisplayName("DB에 사진 행이 있는 파일은 오래됐어도 그대로 둔다")
    void keepFileInDb() {
        List<String> keys = registerProductWithTwoImages();
        keys.forEach(key -> storage.uploadedAgo(key, Duration.ofDays(2)));

        int orphanCount = cleanUp(false);

        assertThat(orphanCount).isZero();
        assertThat(keys).allSatisfy(key -> assertThat(storage.exists(key)).isTrue());
    }

    // 소프트 삭제된 사진은 행이 남아 있어 주문 내역 등에서 쓸 수 있다
    @Test
    @DisplayName("소프트 삭제된(DELETED) 사진의 파일도 DB에 행이 있으므로 그대로 둔다")
    void keepSoftDeletedImageFile() {
        List<String> keys = registerProductWithTwoImages();
        keys.forEach(key -> storage.uploadedAgo(key, Duration.ofDays(2)));
        transactionTemplate.executeWithoutResult(s -> queryFactory.update(productImage)
                .set(productImage.status, ProductStatus.DELETED)
                .execute());

        int orphanCount = cleanUp(false);

        assertThat(orphanCount).isZero();
        assertThat(keys).allSatisfy(key -> assertThat(storage.exists(key)).isTrue());
    }

    @Test
    @DisplayName("dry-run이면 고아 파일 수만 세고 지우지 않는다")
    void dryRunKeepsFiles() {
        storage.putFile("posts/1/orphan.jpg", Instant.now().minus(Duration.ofDays(2)));

        int orphanCount = cleanUp(true);

        assertThat(orphanCount).isEqualTo(1);
        assertThat(storage.exists("posts/1/orphan.jpg")).isTrue();
    }

    // 한 번에 1000개씩 읽으므로 그보다 많으면 여러 페이지를 이어 읽어야 한다
    @Test
    @DisplayName("파일이 한 페이지(1000개)보다 많아도 끝까지 이어 읽어 모두 정리한다")
    void readAllPages() {
        Instant old = Instant.now().minus(Duration.ofDays(2));
        int total = ProductImageOrphanCleanUseCase.PAGE_SIZE + 5;
        for (int i = 0; i < total; i++) {
            storage.putFile("posts/1/orphan-%04d.jpg".formatted(i), old);
        }

        int orphanCount = cleanUp(false);

        assertThat(orphanCount).isEqualTo(total);
        assertThat(storage.savedCount()).isZero();
    }

    @Test
    @DisplayName("사진 폴더(posts/) 밖의 파일은 건드리지 않는다")
    void ignoreOutsidePrefix() {
        storage.putFile("other/keep.jpg", Instant.now().minus(Duration.ofDays(2)));

        int orphanCount = cleanUp(false);

        assertThat(orphanCount).isZero();
        assertThat(storage.exists("other/keep.jpg")).isTrue();
    }

    private int cleanUp(boolean dryRun) {
        return productImageOrphanCleanUseCase.cleanUp(Instant.now().minus(GRACE), dryRun);
    }

    // 판매글 + 상품 1개(사진 2장)를 실제로 등록하고, 저장된 사진 키를 돌려준다(파일 + DB 행이 모두 있음)
    private List<String> registerProductWithTwoImages() {
        SalesPost post = salesPostCreateUseCase.saveSalesPost(
                new CreateSalesPostRequest(null, SELLER_ID, "아이폰 팝니다", "상태 좋아요", List.of()));
        CreateProduct product = new CreateProduct(null, SELLER_ID, post.getId(), 1L, "아이폰", "설명", 10000L,
                List.of(new CreateProductImage(null, SELLER_ID, null, null, 0, null),
                        new CreateProductImage(null, SELLER_ID, null, null, 1, null)),
                List.of(new CreateProductOption(CLOTHING_SIZE, SIZE_L)));
        productFacade.registerProduct(new CreateProductRequest(post.getId(), List.of(product)),
                List.of(TestImageFiles.jpeg(), TestImageFiles.png()));

        return productImageRepository.findAll().stream()
                .map(ProductImage::getS3Key)
                .toList();
    }
}
