package com.cartmate.cartmate.boundedContext.orderService.order.out;

import com.cartmate.cartmate.boundedContext.orderService.order.exception.OrderErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.http.InternalApiClient;
import com.cartmate.cartmate.global.http.InternalApiClient.InternalServerUnavailableException;
import com.cartmate.cartmate.shared.order.OrderType;
import com.cartmate.cartmate.shared.product.dto.InventoryReleaseRequest;
import com.cartmate.cartmate.shared.product.dto.InventoryReserveRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class HttpOrderInventoryClientTest {

    private static final List<InventoryReserveRequest.Item> ITEMS = List.of(new InventoryReserveRequest.Item(4L, 2));

    @Mock
    private InternalApiClient productServerApiClient;

    private HttpOrderInventoryClient client;

    @BeforeEach
    void setUp() {
        client = new HttpOrderInventoryClient(productServerApiClient);
    }

    @Test
    @DisplayName("선점 · 선점 해제는 상품 서버 내부 API로 주문 종류 · 주문 ID · 재고 · 수량을 보낸다")
    void sendsRequests() {
        client.reserve(OrderType.PERSONAL, 15L, ITEMS);
        client.release(OrderType.PERSONAL, 15L);

        then(productServerApiClient).should().post(eq(HttpOrderInventoryClient.RESERVE_PATH),
                eq(new InventoryReserveRequest(OrderType.PERSONAL, 15L, ITEMS)), eq(HttpOrderInventoryClient.NO_DATA));
        then(productServerApiClient).should().post(eq(HttpOrderInventoryClient.RELEASE_PATH),
                eq(new InventoryReleaseRequest(OrderType.PERSONAL, 15L)), eq(HttpOrderInventoryClient.NO_DATA));
    }

    @Test
    @DisplayName("상품 서버의 재고 부족은 ORDER_OUT_OF_STOCK으로 바꾼다")
    void translatesOutOfStock() {
        givenReserveFails(new ServiceException("409-PRODUCT_INVENTORY_OUT_OF_STOCK", "재고가 부족한 상품이 있습니다."));

        assertThatThrownBy(() -> client.reserve(OrderType.PERSONAL, 15L, ITEMS))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_OUT_OF_STOCK.getMsg());
    }

    @Test
    @DisplayName("살 수 없거나 없는 재고는 ORDER_PRODUCT_NOT_PURCHASABLE로 바꾼다")
    void translatesNotPurchasable() {
        givenReserveFails(new ServiceException("409-PRODUCT_INVENTORY_NOT_PURCHASABLE", "지금 구매할 수 없는 상품이 있습니다."));

        assertThatThrownBy(() -> client.reserve(OrderType.PERSONAL, 15L, ITEMS))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_PRODUCT_NOT_PURCHASABLE.getMsg());
    }

    @Test
    @DisplayName("연결 실패는 바꾸지 않고 그대로 던진다(선점됐는지 모름)")
    void keepsUnavailable() {
        InternalServerUnavailableException unavailable = new InternalServerUnavailableException("PRODUCT", "상품", null);
        givenReserveFails(unavailable);

        assertThatThrownBy(() -> client.reserve(OrderType.PERSONAL, 15L, ITEMS)).isSameAs(unavailable);
    }

    private void givenReserveFails(RuntimeException failure) {
        given(productServerApiClient.post(eq(HttpOrderInventoryClient.RESERVE_PATH), any(), eq(HttpOrderInventoryClient.NO_DATA)))
                .willThrow(failure);
    }
}
