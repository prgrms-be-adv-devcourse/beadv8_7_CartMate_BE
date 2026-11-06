package com.cartmate.cartmate.boundedContext.productService.post.app;

import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostProduct;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostProductImage;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostProductOption;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostSaleStatus;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateProductOption;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateSalesPostProduct;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateSalesPostProductImage;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateSalesPostRequest;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductImageRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductOptionRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostRepository;
import com.cartmate.cartmate.boundedContext.productService.product.app.TestImageFiles;
import com.cartmate.cartmate.boundedContext.productService.product.domain.Product;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductImage;
import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductOption;
import com.cartmate.cartmate.boundedContext.productService.product.out.FakeImageStorage;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

/**
 * 판매글 등록부터 끝까지: post 저장 → 상품 API(실제 HTTP) → product 저장 → post 복제본 동기화 → 동기화 완료 → 판매글 완료 처리.
 *
 * <p>post는 같은 서버의 상품 API를 HTTP로 부르므로 실제 웹 서버를 띄운다.
 * SalesPostApiClient는 만들어질 때 주소를 정하는데 그때는 랜덤 포트가 아직 없어서, 빈 포트를 미리 골라 서버와 주소에 같이 넣는다.
 * product 쪽 등록 이벤트 · 복제본 동기화 · 동기화 완료는 모두 HTTP 응답 전에 서버 스레드에서 끝나므로 등록 직후 바로 검증한다.
 *
 * <p>보상 시 사진 파일은 지우지 않는다(스케줄러로 따로 정리할 예정). 그래서 여기서는 DB만 확인한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = "cartmate.scheduling.enabled=false")
@ActiveProfiles("test")
class SalesPostRegisterFlowTest {

    private static final int PORT = freePort();
    private static final Long SELLER_ID = 7L;

    @DynamicPropertySource
    static void serverAndProductUrl(DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> PORT);
        registry.add("product.global.internalUrl", () -> "http://localhost:" + PORT);
    }

    @Autowired
    SalesPostFacade salesPostFacade;
    @MockitoSpyBean
    SalesPostCreateUseCase salesPostCreateUseCase;

    @Autowired
    SalesPostRepository salesPostRepository;
    @Autowired
    SalesPostProductRepository salesPostProductRepository;
    @Autowired
    SalesPostProductImageRepository salesPostProductImageRepository;
    @Autowired
    SalesPostProductOptionRepository salesPostProductOptionRepository;

    @Autowired
    ProductRepository productRepository;
    @Autowired
    ProductImageRepository productImageRepository;
    @Autowired
    ProductOptionRepository productOptionRepository;
    @Autowired
    FakeImageStorage storage;

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
        reset(salesPostCreateUseCase);
    }

    @Test
    @DisplayName("성공: 상품 등록까지 끝나면 판매글이 판매중(ON_SALE)으로 완료 처리된다")
    void postBecomesOnSaleAfterRegister() {
        salesPostFacade.registerSalesPost(request(), images());

        List<SalesPost> posts = salesPostRepository.findAll();
        assertThat(posts).hasSize(1);
        assertThat(posts.getFirst().getStatus()).isEqualTo(SalesPostSaleStatus.ON_SALE);
    }

    @Test
    @DisplayName("성공: product에 상품이 저장되고, post에 같은 값의 복제본이 생기고, product의 동기화 상태가 켜진다")
    void productIsSyncedToPostAfterRegister() {
        salesPostFacade.registerSalesPost(request(), images());

        Long postId = salesPostRepository.findAll().getFirst().getId();

        List<Product> products = productRepository.findAll();
        assertThat(products).hasSize(1);
        Product product = products.getFirst();
        assertThat(product.getSalesPostId()).isEqualTo(postId);
        assertThat(product.getSellerId()).isEqualTo(SELLER_ID);
        assertThat(product.getName()).isEqualTo("아이폰");
        assertThat(product.isSyncStatus()).isTrue();

        List<ProductImage> productImages = productImageRepository.findAll();
        assertThat(productImages).hasSize(2);
        assertThat(productOptionRepository.findAll())
                .extracting(ProductOption::getProductTypeId, ProductOption::getProductOptionId)
                .containsExactlyInAnyOrder(tuple(1L, 100L), tuple(2L, 200L));

        List<SalesPostProduct> copies = salesPostProductRepository.findAll();
        assertThat(copies).hasSize(1);
        SalesPostProduct copy = copies.getFirst();
        assertThat(copy.getId()).isEqualTo(product.getId());
        assertThat(copy.getSalesPost().getId()).isEqualTo(postId);
        assertThat(copy.getSellerId()).isEqualTo(product.getSellerId());
        assertThat(copy.getCategoryId()).isEqualTo(product.getCategoryId());
        assertThat(copy.getName()).isEqualTo(product.getName());
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
    }

    // 상품과 복제본이 이미 만들어진 뒤에 실패하는 경우. 보상이 실제로 되돌려야 할 데이터가 있다
    @Test
    @DisplayName("보상: 상품 저장 후 완료 처리에서 실패하면 판매글 · 상품 · 복제본이 모두 지워진다")
    void compensateWhenCompleteFailsAfterProductsSaved() {
        doThrow(new RuntimeException("테스트용 완료 처리 실패"))
                .when(salesPostCreateUseCase).completeSave(anyLong());

        assertThatThrownBy(() -> salesPostFacade.registerSalesPost(request(), images()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("테스트용 완료 처리 실패");

        assertNothingLeft();
    }

    // 상품 API가 가짜 이미지를 거절해 product에는 처음부터 아무것도 저장되지 않는 경우
    @Test
    @DisplayName("보상: 상품 API가 실패하면 판매글이 지워지고 product · post 어디에도 데이터가 남지 않는다")
    void compensateWhenProductApiFails() {
        assertThatThrownBy(() -> salesPostFacade.registerSalesPost(request(), List.of(TestImageFiles.jpeg(), TestImageFiles.fake())))
                .isInstanceOf(ServiceException.class);

        assertNothingLeft();
    }

    private void assertNothingLeft() {
        assertThat(salesPostRepository.count()).isZero();
        assertThat(productRepository.count()).isZero();
        assertThat(productImageRepository.count()).isZero();
        assertThat(productOptionRepository.count()).isZero();
        assertThat(salesPostProductRepository.count()).isZero();
        assertThat(salesPostProductImageRepository.count()).isZero();
        assertThat(salesPostProductOptionRepository.count()).isZero();
    }

    private CreateSalesPostRequest request() {
        List<CreateSalesPostProductImage> productImages = List.of(
                new CreateSalesPostProductImage(null, null, null, 0, null),
                new CreateSalesPostProductImage(null, null, null, 1, null)
        );
        List<CreateProductOption> options = List.of(new CreateProductOption(1L, 100L), new CreateProductOption(2L, 200L));
        CreateSalesPostProduct product = new CreateSalesPostProduct(
                null, null, 1L, "아이폰", "설명", 10000L, productImages, options);

        return new CreateSalesPostRequest(null, SELLER_ID, "아이폰 팝니다", "상태 좋아요", List.of(product));
    }

    private List<MultipartFile> images() {
        return List.of(TestImageFiles.jpeg(), TestImageFiles.png());
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
