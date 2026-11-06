package com.cartmate.cartmate.boundedContext.productService.post.app;

import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostProduct;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostProductImage;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostSaleStatus;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostUpdateStatus;
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
import com.cartmate.cartmate.boundedContext.productService.product.out.FakeImageStorage;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductImageRepository;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductOptionRepository;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.post.event.SalesPostHiddenEvent;
import com.cartmate.cartmate.shared.post.event.SalesPostHideType;
import com.cartmate.cartmate.shared.product.ProductStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 판매글 삭제: post DELETING → 상품 삭제 API(실제 HTTP) → product 삭제 → ProductListDeletedEvent로 post 복제본 삭제
 * → 판매글 DELETED + 진행 중 거래 정리를 위한 숨김 이벤트(order-service가 받음).
 *
 * <p>등록 E2E와 같은 이유로 실제 웹 서버를 빈 포트로 띄우고, 판매글 · 상품 · 복제본은 실제 등록 흐름으로 만들어 둔다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = "cartmate.scheduling.enabled=false")
@ActiveProfiles("test")
@RecordApplicationEvents
class SalesPostDeleteFlowTest {

    private static final int PORT = freePort();
    private static final Long SELLER_ID = 7L;
    private static final Long OTHER_SELLER_ID = 8L;

    @DynamicPropertySource
    static void serverAndProductUrl(DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> PORT);
        registry.add("product.global.internalUrl", () -> "http://localhost:" + PORT);
    }

    @Autowired
    SalesPostFacade salesPostFacade;
    @Autowired
    ApplicationEvents events;
    @Autowired
    TransactionTemplate transactionTemplate;

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

    Long postId;

    // 판매글 + 상품 2개를 실제 등록 흐름으로 만들어 둔다 (post 복제본까지 생성됨)
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

        salesPostFacade.registerSalesPost(request(), List.of(TestImageFiles.jpeg(), TestImageFiles.png()));
        postId = salesPostRepository.findAll().getFirst().getId();
    }

    @Test
    @DisplayName("판매글을 삭제하면 판매글 · 상품 · 복제본이 모두 삭제 상태가 된다")
    void deleteSalesPostDeletesEverything() {
        salesPostFacade.deleteSalesPostByUser(postId, SELLER_ID);

        SalesPost post = salesPostRepository.findById(postId).orElseThrow();
        assertThat(post.getStatus()).isEqualTo(SalesPostSaleStatus.DELETED);
        assertThat(post.getHiddenAt()).isNotNull();

        assertThat(productRepository.findAll())
                .hasSize(2)
                .extracting(Product::getStatus)
                .containsOnly(ProductStatus.DELETED);
        assertThat(productImageRepository.findAll())
                .extracting(ProductImage::getStatus)
                .containsOnly(ProductStatus.DELETED);
        assertThat(productOptionRepository.count()).isZero();

        assertThat(salesPostProductRepository.findAll())
                .hasSize(2)
                .extracting(SalesPostProduct::getStatus)
                .containsOnly(ProductStatus.DELETED);
        assertThat(salesPostProductImageRepository.findAll())
                .extracting(SalesPostProductImage::getStatus)
                .containsOnly(ProductStatus.DELETED);
        assertThat(salesPostProductOptionRepository.count()).isZero();
    }

    @Test
    @DisplayName("삭제가 끝나면 진행 중 거래 정리를 위한 숨김 이벤트가 나간다")
    void publishHiddenEventAfterDelete() {
        List<Long> copyIds = salesPostProductRepository.findAll().stream()
                .map(SalesPostProduct::getId)
                .toList();

        salesPostFacade.deleteSalesPostByUser(postId, SELLER_ID);

        List<SalesPostHiddenEvent> hidden = events.stream(SalesPostHiddenEvent.class).toList();
        assertThat(hidden).hasSize(1);
        SalesPostHiddenEvent event = hidden.getFirst();
        assertThat(event.postId()).isEqualTo(postId);
        assertThat(event.sellerId()).isEqualTo(SELLER_ID);
        assertThat(event.hideType()).isEqualTo(SalesPostHideType.SELLER_DELETED);
        assertThat(event.productIds()).hasSize(2).containsExactlyInAnyOrderElementsOf(copyIds);
    }

    @Test
    @DisplayName("다른 판매자는 판매글을 삭제할 수 없다")
    void otherSellerCannotDeleteSalesPost() {
        assertThatThrownBy(() -> salesPostFacade.deleteSalesPostByUser(postId, OTHER_SELLER_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageStartingWith("POST_ACCESS_DENIED");

        assertThat(salesPostRepository.findById(postId).orElseThrow().getStatus()).isEqualTo(SalesPostSaleStatus.ON_SALE);
        assertThat(productRepository.findAll()).extracting(Product::getStatus).containsOnly(ProductStatus.ON_SALE);
    }

    // 상품 삭제 API가 실패해 판매글이 DELETING에 멈춘 상황. 다시 삭제를 시작하지 못하고 상품도 건드리지 않아야 한다
    @Test
    @DisplayName("삭제 중(DELETING)인 판매글은 다시 삭제할 수 없다")
    void cannotDeleteWhileDeleting() {
        transactionTemplate.executeWithoutResult(status ->
                salesPostRepository.startDelete(postId, SalesPostUpdateStatus.editableStatuses()));

        assertThatThrownBy(() -> salesPostFacade.deleteSalesPostByUser(postId, SELLER_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageStartingWith("POST_SALE_PENDING_STATUS");

        assertThat(salesPostRepository.findById(postId).orElseThrow().getStatus()).isEqualTo(SalesPostSaleStatus.DELETING);
        assertThat(productRepository.findAll()).extracting(Product::getStatus).containsOnly(ProductStatus.ON_SALE);
    }

    // 상품 2개, 각각 사진 1장 · 옵션 1개
    private CreateSalesPostRequest request() {
        CreateSalesPostProduct iphone = new CreateSalesPostProduct(null, null, 1L, "아이폰", "설명", 10000L,
                List.of(new CreateSalesPostProductImage(null, null, null, 0, null)),
                List.of(new CreateProductOption(1L, 100L)));
        CreateSalesPostProduct charger = new CreateSalesPostProduct(null, null, 1L, "충전기", "설명", 3000L,
                List.of(new CreateSalesPostProductImage(null, null, null, 0, null)),
                List.of(new CreateProductOption(2L, 200L)));

        return new CreateSalesPostRequest(null, SELLER_ID, "아이폰 팝니다", "상태 좋아요", List.of(iphone, charger));
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
