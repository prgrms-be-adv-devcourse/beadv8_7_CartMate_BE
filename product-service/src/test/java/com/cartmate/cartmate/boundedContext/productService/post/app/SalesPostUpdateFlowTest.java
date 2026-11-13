package com.cartmate.cartmate.boundedContext.productService.post.app;

import static com.cartmate.cartmate.support.TestInventories.inventory;
import static com.cartmate.cartmate.support.SeedOptions.*;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPost;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostProduct;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostRecoveryRepository;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostSaleStatus;
import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostUpdateStatus;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateProductOption;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateSalesPostProduct;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateSalesPostProductImage;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateSalesPostRequest;
import com.cartmate.cartmate.boundedContext.productService.post.dto.UpdateSalesPostProduct;
import com.cartmate.cartmate.boundedContext.productService.post.dto.UpdateSalesPostProductImage;
import com.cartmate.cartmate.boundedContext.productService.post.dto.UpdateSalesPostInventory;
import com.cartmate.cartmate.boundedContext.productService.product.domain.Inventory;
import com.cartmate.cartmate.boundedContext.productService.product.out.InventoryRepository;
import com.cartmate.cartmate.boundedContext.productService.post.dto.UpdateSalesPostRequest;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

/**
 * 판매글 수정: 스냅샷용 상품 조회(GET) → 상품 수정(PATCH, 수정한 상품을 응답으로 받음) → 복제본 갱신 · 수정 완료(한 트랜잭션).
 * product가 요청을 거절(4xx)하면 상태만 되돌리고, 상품이 바뀐 뒤 실패하면 스냅샷으로 복구(PUT restore)한다.
 *
 * <p>등록 E2E와 같은 이유로 실제 웹 서버를 빈 포트로 띄우고, 판매글 · 상품 · 복제본은 실제 등록 흐름으로 만들어 둔다.
 * 사진은 그대로 두고 이름 · 가격만 바꿔서, 업로드 · 삭제 키 처리 때문에 실패 원인이 가려지지 않게 한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = "cartmate.scheduling.enabled=false")
@ActiveProfiles("test")
class SalesPostUpdateFlowTest {

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
    @MockitoSpyBean
    SalesPostUpdateUseCase salesPostUpdateUseCase;
    @MockitoSpyBean
    SalesPostSyncProductUseCase salesPostSyncProductUseCase;
    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    SalesPostRepository salesPostRepository;
    @Autowired
    SalesPostRecoveryRepository salesPostRecoveryRepository;
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
    InventoryRepository inventoryRepository;
    @Autowired
    FakeImageStorage storage;

    Long postId;
    Long productId;

    // 판매글 + 상품 1개(사진 2장, 옵션 1개)를 실제 등록 흐름으로 만들어 둔다 (post 복제본까지 생성됨)
    @BeforeEach
    void setUp() {
        salesPostRecoveryRepository.deleteAll();
        salesPostProductImageRepository.deleteAll();
        salesPostProductOptionRepository.deleteAll();
        salesPostProductRepository.deleteAll();
        salesPostRepository.deleteAll();
        productImageRepository.deleteAll();
        productOptionRepository.deleteAll();
        productRepository.deleteAll();
        storage.reset();
        reset(salesPostUpdateUseCase, salesPostSyncProductUseCase);

        CreateSalesPostProduct product = new CreateSalesPostProduct(null, null, 1L, "아이폰", "설명", 10000L,
                List.of(new CreateSalesPostProductImage(null, null, null, 0, null),
                        new CreateSalesPostProductImage(null, null, null, 1, null)),
                List.of(inventory(new CreateProductOption(CLOTHING_SIZE, SIZE_L))));
        salesPostFacade.registerSalesPost(
                new CreateSalesPostRequest(null, SELLER_ID, "아이폰 팝니다", "상태 좋아요", List.of(product)),
                List.of(TestImageFiles.jpeg(), TestImageFiles.png()));

        postId = salesPostRepository.findAll().getFirst().getId();
        productId = productRepository.findAll().getFirst().getId();
    }

    @Test
    @DisplayName("성공: 판매글과 상품이 수정되고, post 복제본에도 반영되고, 수정 완료 상태가 된다")
    void updateSuccess() {
        salesPostFacade.updateSalesPostByUser(updateRequest(SELLER_ID, "새 제목", "새이름", 30000L), List.of());

        SalesPost post = salesPostRepository.findById(postId).orElseThrow();
        assertThat(post.getTitle()).isEqualTo("새 제목");
        assertThat(post.getContent()).isEqualTo("새 내용");
        assertThat(post.getUpdateStatus()).isEqualTo(SalesPostUpdateStatus.COMPLETED);

        Product product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getName()).isEqualTo("새이름");
        assertThat(product.getBasePrice()).isEqualTo(30000L);

        SalesPostProduct copy = salesPostProductRepository.findById(productId).orElseThrow();
        assertThat(copy.getName()).isEqualTo("새이름");
        assertThat(copy.getBasePrice()).isEqualTo(30000L);

        assertThat(salesPostRecoveryRepository.findBySalesPostId(postId)).isEmpty();
    }

    // id 없는 새 사진을 넣고 파일은 보내지 않아 product가 PRODUCT_UPDATE_INVALID(400)로 거절한다.
    // 상품 쪽은 바뀐 게 없으므로 복구 절차 없이 판매글 상태만 수정 전으로 되돌려야 한다
    @Test
    @DisplayName("거절: product가 수정 요청을 거절(4xx)하면 상태만 수정 전으로 되돌리고 상품은 그대로다")
    void revertWhenProductRejects() {
        UpdateSalesPostRequest request = updateRequest(SELLER_ID, "새 제목", "새이름", 30000L);
        request.getProductList().getFirst().getProductImageList()
                .add(new UpdateSalesPostProductImage(null, productId, null, 2));

        assertThatThrownBy(() -> salesPostFacade.updateSalesPostByUser(request, List.of()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("PRODUCT_UPDATE_INVALID");

        SalesPost post = salesPostRepository.findById(postId).orElseThrow();
        assertThat(post.getTitle()).isEqualTo("아이폰 팝니다");
        assertThat(post.getUpdateStatus()).isEqualTo(SalesPostUpdateStatus.NEW);

        Product product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getName()).isEqualTo("아이폰");
        assertThat(product.getBasePrice()).isEqualTo(10000L);

        assertThat(salesPostRecoveryRepository.findBySalesPostId(postId)).isEmpty();
    }

    // 상품은 수정됐는데 post가 응답으로 복제본을 맞추지 못한 경우. 복제본 갱신 실패도 수정 실패로 보고 상품을 복구한다
    // (복구할 때 복제본도 다시 맞추므로, 실패는 수정 때 한 번만 일으킨다)
    @Test
    @DisplayName("복구: 복제본 갱신이 실패하면 상품과 복제본을 수정 전으로 되돌린다")
    void recoverWhenReplicaSyncFails() {
        doThrow(new RuntimeException("테스트용 복제본 갱신 실패"))
                .doCallRealMethod()
                .when(salesPostSyncProductUseCase).syncProduct(anyList());

        assertThatThrownBy(() -> salesPostFacade.updateSalesPostByUser(
                updateRequest(SELLER_ID, "새 제목", "새이름", 30000L), List.of()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("테스트용 복제본 갱신 실패");

        Product product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getName()).isEqualTo("아이폰");
        assertThat(product.getBasePrice()).isEqualTo(10000L);

        SalesPostProduct copy = salesPostProductRepository.findById(productId).orElseThrow();
        assertThat(copy.getName()).isEqualTo("아이폰");

        SalesPost post = salesPostRepository.findById(postId).orElseThrow();
        assertThat(post.getTitle()).isEqualTo("아이폰 팝니다");
        assertThat(post.getUpdateStatus()).isEqualTo(SalesPostUpdateStatus.RECOVERED);
        assertThat(salesPostRecoveryRepository.findBySalesPostId(postId)).isEmpty();
    }

    // 상품은 이미 수정된 뒤 판매글 마무리에서 실패하는 경우. 스냅샷으로 상품을 되돌리는 복구(PUT restore)가 실제로 돈다
    @Test
    @DisplayName("복구: 상품 수정 후 판매글 마무리가 실패하면 상품과 복제본을 수정 전으로 되돌린다")
    void recoverWhenCompleteFails() {
        doThrow(new RuntimeException("테스트용 완료 실패"))
                .when(salesPostUpdateUseCase).markCompleted(anyLong());

        assertThatThrownBy(() -> salesPostFacade.updateSalesPostByUser(
                updateRequest(SELLER_ID, "새 제목", "새이름", 30000L), List.of()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("테스트용 완료 실패");

        Product product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getName()).isEqualTo("아이폰");
        assertThat(product.getBasePrice()).isEqualTo(10000L);

        SalesPostProduct copy = salesPostProductRepository.findById(productId).orElseThrow();
        assertThat(copy.getName()).isEqualTo("아이폰");
        assertThat(copy.getBasePrice()).isEqualTo(10000L);

        SalesPost post = salesPostRepository.findById(postId).orElseThrow();
        assertThat(post.getTitle()).isEqualTo("아이폰 팝니다");
        assertThat(post.getUpdateStatus()).isEqualTo(SalesPostUpdateStatus.RECOVERED);

        assertThat(salesPostRecoveryRepository.findBySalesPostId(postId)).isEmpty();
    }

    @Test
    @DisplayName("다른 판매자는 판매글을 수정할 수 없다")
    void otherSellerCannotUpdate() {
        assertThatThrownBy(() -> salesPostFacade.updateSalesPostByUser(
                updateRequest(OTHER_SELLER_ID, "새 제목", "새이름", 30000L), List.of()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("POST_ACCESS_DENIED");

        SalesPost post = salesPostRepository.findById(postId).orElseThrow();
        assertThat(post.getTitle()).isEqualTo("아이폰 팝니다");
        assertThat(post.getUpdateStatus()).isEqualTo(SalesPostUpdateStatus.NEW);
        assertThat(productRepository.findById(productId).orElseThrow().getName()).isEqualTo("아이폰");
    }

    @Test
    @DisplayName("삭제 중(DELETING)인 판매글은 수정할 수 없다")
    void cannotUpdateWhileDeleting() {
        transactionTemplate.executeWithoutResult(status ->
                salesPostRepository.startDelete(postId, SalesPostUpdateStatus.editableStatuses()));

        assertThatThrownBy(() -> salesPostFacade.updateSalesPostByUser(
                updateRequest(SELLER_ID, "새 제목", "새이름", 30000L), List.of()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("POST_SALE_PENDING_STATUS");

        SalesPost post = salesPostRepository.findById(postId).orElseThrow();
        assertThat(post.getStatus()).isEqualTo(SalesPostSaleStatus.DELETING);
        assertThat(productRepository.findById(productId).orElseThrow().getName()).isEqualTo("아이폰");
    }

    // 기존 사진 2장(id · s3Key · 순서 유지)과 기존 옵션(id 유지)은 그대로 두고 이름 · 가격만 바꾼 요청
    private UpdateSalesPostRequest updateRequest(Long sellerId, String title, String name, Long basePrice) {
        List<UpdateSalesPostProductImage> images = new ArrayList<>(productImageRepository.findAll().stream()
                .sorted(Comparator.comparingInt(ProductImage::getImageSeq))
                .map(i -> new UpdateSalesPostProductImage(i.getId(), productId, i.getS3Key(), i.getImageSeq()))
                .toList());
        // 기존 재고(id 유지)는 그대로 둔다. 조합은 바뀌지 않으므로 옵션 목록은 보내지 않는다
        List<UpdateSalesPostInventory> options = inventoryRepository.findAll().stream()
                .filter(Inventory::isActive)
                .map(i -> new UpdateSalesPostInventory(i.getId(), null, i.getAdditionalPrice(), i.getStockQuantity()))
                .toList();
        UpdateSalesPostProduct product = new UpdateSalesPostProduct(productId, 1L, name, "설명", basePrice, images, options);

        return new UpdateSalesPostRequest(postId, sellerId, title, "새 내용", List.of(product));
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
