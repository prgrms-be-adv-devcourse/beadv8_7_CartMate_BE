package com.cartmate.cartmate.boundedContext.orderService.order.out;

import com.cartmate.cartmate.global.http.InternalApiClient;
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
class HttpOrderProductClientTest {

    @Mock
    private InternalApiClient productServerApiClient;

    private HttpOrderProductClient productClient;

    @BeforeEach
    void setUp() {
        productClient = new HttpOrderProductClient(productServerApiClient);
    }

    @Test
    @DisplayName("상품 정보를 재고 ID가 상품 ID와 같은 주문 상품으로 변환하고 없는 상품은 제외한다")
    void mapsProductsAndExcludesMissing() {
        given(productServerApiClient.get(eq(HttpOrderProductClient.PATH), eq(HttpOrderProductClient.PRODUCTS), eq("10,99")))
                .willReturn(List.of(new ProductForPurchase(10L, 7L, "축구공", 25000L, true, "https://example.com/ball.png", 3L)));

        assertThat(productClient.findAllByInventoryIds(List.of(10L, 99L))).containsExactly(
                new OrderProductInfo(10L, 10L, 7L, "축구공", null, 25000L, "https://example.com/ball.png", true));
    }
}
