package com.cartmate.cartmate.boundedContext.productService.post.app;

import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateInventory;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateProductOption;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateSalesPostProduct;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateSalesPostProductImage;
import com.cartmate.cartmate.boundedContext.productService.post.dto.CreateSalesPostRequest;
import com.cartmate.cartmate.boundedContext.productService.post.dto.SelectSalesPostInventory;
import com.cartmate.cartmate.boundedContext.productService.post.dto.SelectSalesPostOptionGroup;
import com.cartmate.cartmate.boundedContext.productService.post.dto.SelectSalesPostProduct;
import com.cartmate.cartmate.boundedContext.productService.post.dto.SelectSalesPostRequest;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostApiClient;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductImageRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductOptionRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostProductRepository;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostRepository;
import com.cartmate.cartmate.boundedContext.productService.product.app.TestImageFiles;
import com.cartmate.cartmate.boundedContext.productService.product.out.FakeImageStorage;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductImageRepository;
import com.cartmate.cartmate.boundedContext.productService.product.out.ProductRepository;
import com.cartmate.cartmate.support.ProductServiceRedisContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.Objects;

import static com.cartmate.cartmate.support.SeedOptions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 판매글 상세: 복제본(판매글 · 상품 · 사진)에 상품 서버의 선택 목록 · 재고를 붙인다. 재고 부분은 Redis에 2초 캐시한다.
 * post → product HTTP 호출이 실제로 일어나도록 웹 서버를 빈 포트로 띄우고, 캐시용 Redis 컨테이너를 쓴다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = "cartmate.scheduling.enabled=false")
@ActiveProfiles("test")
class SalesPostDetailFlowTest {

    private static final int PORT;

    static {
        ProductServiceRedisContainer.start();   // Redis 컨테이너를 먼저 띄운 뒤 빈 포트를 고른다(포트 충돌 방지)
        PORT = freePort();
    }

    private static final Long SELLER_ID = 7L;
    private static final Long BASE_PRICE = 189000L;

    @DynamicPropertySource
    static void serverProductUrlAndRedis(DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> PORT);
        registry.add("product.global.internalUrl", () -> "http://localhost:" + PORT);
        ProductServiceRedisContainer.register(registry);
    }

    @Autowired
    SalesPostFacade salesPostFacade;
    @MockitoSpyBean
    SalesPostApiClient salesPostApiClient;
    @Autowired
    CacheManager cacheManager;

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
    FakeImageStorage storage;

    Long postId;
    Long productId;

    // 축구화 1개: 재고 "265 / 블랙"(추가금액 0, 3개), "255 / 화이트"(추가금액 5,000, 0개 = 품절)
    @BeforeEach
    void setUp() {
        salesPostProductImageRepository.deleteAll();
        salesPostProductOptionRepository.deleteAll();
        salesPostProductRepository.deleteAll();
        salesPostRepository.deleteAll();
        productImageRepository.deleteAll();
        productRepository.deleteAll();
        storage.reset();
        Objects.requireNonNull(cacheManager.getCache(SalesPostDetailInventoryLoader.CACHE_NAME)).clear();

        CreateSalesPostProduct shoes = new CreateSalesPostProduct(null, null, 3L, "머큐리얼 FG", "천연잔디용", BASE_PRICE,
                List.of(new CreateSalesPostProductImage(null, null, null, 0, null)),
                List.of(
                        new CreateInventory(List.of(
                                new CreateProductOption(SHOE_SIZE, SHOE_265), new CreateProductOption(COLOR, BLACK)), 0L, 3),
                        new CreateInventory(List.of(
                                new CreateProductOption(SHOE_SIZE, SHOE_255), new CreateProductOption(COLOR, WHITE)), 5000L, 0)));
        salesPostFacade.registerSalesPost(
                new CreateSalesPostRequest(null, SELLER_ID, "축구화 팝니다", "새 상품", List.of(shoes)),
                List.of(TestImageFiles.jpeg()));

        postId = salesPostRepository.findAll().getFirst().getId();
        productId = productRepository.findAll().getFirst().getId();
        reset(salesPostApiClient);
    }

    @Test
    @DisplayName("상세: 상품마다 옵션 종류별 선택 목록(중복 없이)과 재고(이름 · 최종 가격 · 구매 가능 수량 · 상품옵션)가 붙는다")
    void detailHasOptionGroupsAndInventories() {
        SelectSalesPostProduct product = detailProduct();

        // 옵션 종류는 재고 이름 순서(사이즈 → 색상), 상품옵션은 ID 순
        assertThat(product.getOptionGroups())
                .extracting(SelectSalesPostOptionGroup::typeId, SelectSalesPostOptionGroup::typeName)
                .containsExactly(tuple(SHOE_SIZE, "신발 사이즈"), tuple(COLOR, "색상"));
        assertThat(product.getOptionGroups().get(0).options())
                .extracting(option -> option.optionId(), option -> option.name())
                .containsExactly(tuple(SHOE_255, "255"), tuple(SHOE_265, "265"));
        assertThat(product.getOptionGroups().get(1).options())
                .extracting(option -> option.optionId(), option -> option.name())
                .containsExactly(tuple(BLACK, "블랙"), tuple(WHITE, "화이트"));

        assertThat(product.getInventoryList())
                .extracting(SelectSalesPostInventory::skuName, SelectSalesPostInventory::appliedPrice,
                        SelectSalesPostInventory::additionalPrice, SelectSalesPostInventory::stockQuantity,
                        SelectSalesPostInventory::optionIds)
                .containsExactlyInAnyOrder(
                        tuple("265 / 블랙", 189000L, 0L, 3, List.of(SHOE_265, BLACK)),
                        tuple("255 / 화이트", 194000L, 5000L, 0, List.of(SHOE_255, WHITE)));
    }

    @Test
    @DisplayName("목록: 선택 목록 · 재고를 붙이지 않고 상품 서버도 부르지 않는다")
    void listHasNoInventories() {
        List<SelectSalesPostProduct> products = salesPostFacade
                .selectSalesPostAll(new SelectSalesPostRequest(), PageRequest.of(0, 10))
                .getData().content().getFirst().getProductList();

        assertThat(products.getFirst().getOptionGroups()).isEmpty();
        assertThat(products.getFirst().getInventoryList()).isEmpty();
        verify(salesPostApiClient, times(0)).getProduct(anyLong());
    }

    @Test
    @DisplayName("캐시: 2초 안에 다시 보면 상품 서버를 다시 부르지 않고, 2초가 지나면 다시 부른다")
    void inventoryIsCachedForTwoSeconds() throws InterruptedException {
        detailProduct();
        detailProduct();
        verify(salesPostApiClient, times(1)).getProduct(postId);
        assertThat(Objects.requireNonNull(cacheManager.getCache(SalesPostDetailInventoryLoader.CACHE_NAME)).get(postId))
                .isNotNull();   // Redis에 들어 있다

        Thread.sleep(2_500);   // TTL 2초
        SelectSalesPostProduct afterExpiry = detailProduct();

        verify(salesPostApiClient, times(2)).getProduct(postId);
        assertThat(afterExpiry.getInventoryList()).hasSize(2);   // 다시 가져와도 같은 내용
    }

    private SelectSalesPostProduct detailProduct() {
        return salesPostFacade.selectSalesPostDetail(postId).getData().getProductList().stream()
                .filter(product -> product.getId().equals(productId))
                .findFirst()
                .orElseThrow();
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
