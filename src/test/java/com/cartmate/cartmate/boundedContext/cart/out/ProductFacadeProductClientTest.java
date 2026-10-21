package com.cartmate.cartmate.boundedContext.cart.out;

import com.cartmate.cartmate.boundedContext.product.app.ProductFacade;
import com.cartmate.cartmate.shared.product.dto.ProductForPurchase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ProductFacadeProductClientTest {

    @Mock
    private ProductFacade productFacade;

    private ProductFacadeProductClient productClient;

    @BeforeEach
    void setUp() {
        productClient = new ProductFacadeProductClient(productFacade);
    }

    @Test
    @DisplayName("상품 정보를 재고 ID가 상품 ID와 같은 단일 옵션 장바구니 상품으로 변환한다")
    void mapsProductToCartProductInfo() {
        given(productFacade.findProductsForPurchase(List.of(10L)))
                .willReturn(List.of(product(10L, 39000L, true)));

        assertThat(productClient.findByInventoryId(10L)).contains(
                new CartProductInfo(10L, 10L, "축구 유니폼", null, 39000, "https://example.com/10.png", true));
    }

    @Test
    @DisplayName("존재하지 않는 상품은 조회 결과에서 빠지고 구매 불가로 취급된다")
    void excludesMissingProduct() {
        given(productFacade.findProductsForPurchase(List.of(10L, 99L)))
                .willReturn(List.of(product(10L, 39000L, true)));

        assertThat(productClient.findAllByInventoryIds(List.of(10L, 99L)))
                .extracting(CartProductInfo::inventoryId)
                .containsExactly(10L);
        assertThat(productClient.findUnavailableInventoryIds(List.of(10L, 99L))).containsExactly(99L);
    }

    @Test
    @DisplayName("상품마다 재고 ID가 상품 ID인 옵션 하나만 만든다")
    void createsSingleOptionPerProduct() {
        given(productFacade.findProductsForPurchase(List.of(10L, 20L)))
                .willReturn(List.of(product(10L, 39000L, true), product(20L, 25000L, false)));

        List<CartProductOptions> result = productClient.findAllProductOptions(List.of(10L, 20L));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).options()).containsExactly(
                new CartProductOptions.Option(10L, null, 39000, true));
        assertThat(result.get(1).purchasable()).isFalse();
        assertThat(result.get(1).options()).containsExactly(
                new CartProductOptions.Option(20L, null, 25000, false));
    }

    private ProductForPurchase product(Long productId, Long basePrice, boolean purchasable) {
        return new ProductForPurchase(productId, 1L, "축구 유니폼", basePrice, purchasable,
                "https://example.com/" + productId + ".png");
    }
}
