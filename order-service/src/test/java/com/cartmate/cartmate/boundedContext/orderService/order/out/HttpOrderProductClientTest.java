package com.cartmate.cartmate.boundedContext.orderService.order.out;

import com.cartmate.cartmate.global.http.InternalApiClient;
import com.cartmate.cartmate.shared.product.dto.InventoryForPurchase;
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
    @DisplayName("재고 정보를 주문 상품으로 변환하고(옵션명 = 재고 이름, 가격 = 적용가) 없는 재고는 제외한다")
    void mapsInventoriesAndExcludesMissing() {
        given(productServerApiClient.get(eq(HttpOrderProductClient.PATH), eq(HttpOrderProductClient.INVENTORIES), eq("101,999")))
                .willReturn(List.of(new InventoryForPurchase(101L, 10L, 7L, "축구공", "5호", 25000L,
                        "https://example.com/ball.png", 3L, true)));

        assertThat(productClient.findAllByInventoryIds(List.of(101L, 999L))).containsExactly(
                new OrderProductInfo(101L, 10L, 7L, "축구공", "5호", 25000L, "https://example.com/ball.png", true));
    }

    @Test
    @DisplayName("빈 요청이면 상품 서버를 부르지 않는다")
    void emptyRequest() {
        assertThat(productClient.findAllByInventoryIds(List.of())).isEmpty();
    }
}
