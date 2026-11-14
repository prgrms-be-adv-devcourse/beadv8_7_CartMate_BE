package com.cartmate.cartmate.boundedContext.orderService.cart.out;

import com.cartmate.cartmate.global.http.InternalApiClient;
import com.cartmate.cartmate.shared.product.dto.InventoryForPurchase;
import com.cartmate.cartmate.shared.product.dto.ProductForPurchase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class HttpProductClientTest {

    @Mock
    private InternalApiClient productServerApiClient;

    private HttpProductClient productClient;

    @BeforeEach
    void setUp() {
        productClient = new HttpProductClient(productServerApiClient);
    }

    @Test
    @DisplayName("재고 정보를 장바구니 상품으로 변환한다(옵션명 = 재고 이름, 가격 = 적용가)")
    void mapsInventoryToCartProductInfo() {
        given(productServerApiClient.get(eq(HttpProductClient.INVENTORY_PATH), eq(HttpProductClient.INVENTORIES), eq("101")))
                .willReturn(List.of(inventory(101L, 10L, "L / 블랙", 39000L, true)));

        assertThat(productClient.findByInventoryId(101L)).contains(
                new CartProductInfo(101L, 10L, "축구 유니폼", "L / 블랙", 39000, "https://example.com/10.png", true));
    }

    @Test
    @DisplayName("존재하지 않는 재고는 조회 결과에서 빠지고 구매 불가로 취급된다")
    void excludesMissingInventory() {
        given(productServerApiClient.get(eq(HttpProductClient.INVENTORY_PATH), eq(HttpProductClient.INVENTORIES), eq("101,999")))
                .willReturn(List.of(inventory(101L, 10L, "L / 블랙", 39000L, true)));

        assertThat(productClient.findAllByInventoryIds(List.of(101L, 999L)))
                .extracting(CartProductInfo::inventoryId)
                .containsExactly(101L);
        assertThat(productClient.findUnavailableInventoryIds(List.of(101L, 999L))).containsExactly(999L);
    }

    @Test
    @DisplayName("상품별로 재고를 옵션으로 묶고, 재고 중 하나라도 구매 가능하면 상품도 구매 가능이다")
    void groupsInventoriesByProduct() {
        given(productServerApiClient.get(eq(HttpProductClient.INVENTORY_BY_PRODUCT_PATH), eq(HttpProductClient.INVENTORIES),
                eq("20,10,30")))
                .willReturn(List.of(
                        inventory(101L, 10L, "L / 블랙", 39000L, true),
                        inventory(102L, 10L, "L / 화이트", 40000L, false),
                        inventory(201L, 20L, "5호", 25000L, false)));

        List<CartProductOptions> result = productClient.findAllProductOptions(List.of(20L, 10L, 30L));

        // 요청한 상품 순서, 재고가 없는 상품(30)은 빠진다
        assertThat(result).extracting(CartProductOptions::productId).containsExactly(20L, 10L);
        assertThat(result.get(0).purchasable()).isFalse();
        assertThat(result.get(1).purchasable()).isTrue();
        assertThat(result.get(1).options()).containsExactly(
                new CartProductOptions.Option(101L, "L / 블랙", 39000, true),
                new CartProductOptions.Option(102L, "L / 화이트", 40000, false));
    }

    @Test
    @DisplayName("매칭용 상품 프로필은 상품 단위 API로 조회한다")
    void findsProductProfilesByProduct() {
        given(productServerApiClient.get(eq(HttpProductClient.PATH), eq(HttpProductClient.PRODUCTS), eq("10")))
                .willReturn(List.of(new ProductForPurchase(10L, 1L, "축구 유니폼", 39000L, true,
                        "https://example.com/10.png", 3L)));

        assertThat(productClient.findAllProductProfiles(List.of(10L)))
                .containsExactly(new CartProductProfile(10L, "축구 유니폼", 3L));
    }

    @Test
    @DisplayName("빈 요청이면 상품 서버를 부르지 않는다")
    void emptyRequest() {
        assertThat(productClient.findAllByInventoryIds(List.of())).isEmpty();
        assertThat(productClient.findAllProductOptions(List.of())).isEmpty();
    }

    private InventoryForPurchase inventory(Long inventoryId, Long productId, String optionName, Long price,
                                           boolean purchasable) {
        return new InventoryForPurchase(inventoryId, productId, 1L, "축구 유니폼", optionName, price,
                "https://example.com/" + productId + ".png", 3L, purchasable);
    }
}
