package com.cartmate.cartmate.boundedContext.orderService.order.out;

import com.cartmate.cartmate.boundedContext.orderService.order.exception.OrderErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.http.InternalApiClient;
import com.cartmate.cartmate.global.http.InternalApiClient.InternalResponse;
import com.cartmate.cartmate.shared.order.OrderType;
import com.cartmate.cartmate.shared.product.dto.InventoryReleaseRequest;
import com.cartmate.cartmate.shared.product.dto.InventoryReserveRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
class HttpOrderInventoryClient implements OrderInventoryClient {

    static final String RESERVE_PATH = "/internal/products/inventories/reserve";
    static final String RELEASE_PATH = "/internal/products/inventories/release";
    static final ParameterizedTypeReference<InternalResponse<Void>> NO_DATA = new ParameterizedTypeReference<>() {};

    private final InternalApiClient productServerApiClient;

    @Override
    public void reserve(OrderType orderType, Long orderId, List<InventoryReserveRequest.Item> items) {
        try {
            productServerApiClient.post(RESERVE_PATH, new InventoryReserveRequest(orderType, orderId, items), NO_DATA);
        } catch (InternalApiClient.InternalServerUnavailableException e) {
            throw e;   // 연결 실패는 그대로(선점됐는지 모름)
        } catch (ServiceException e) {
            throw translate(e);
        }
    }

    @Override
    public void release(OrderType orderType, Long orderId) {
        productServerApiClient.post(RELEASE_PATH, new InventoryReleaseRequest(orderType, orderId), NO_DATA);
    }

    // 상품 서버 오류 코드를 주문 오류로 바꾼다
    private static ServiceException translate(ServiceException e) {
        String resultCode = e.getRsData().getResultCode();
        if (resultCode.endsWith("PRODUCT_INVENTORY_OUT_OF_STOCK")) {
            return new ServiceException(OrderErrorCode.ORDER_OUT_OF_STOCK);
        }
        if (resultCode.endsWith("PRODUCT_INVENTORY_NOT_PURCHASABLE") || resultCode.endsWith("PRODUCT_INVENTORY_NOT_FOUND")) {
            return new ServiceException(OrderErrorCode.ORDER_PRODUCT_NOT_PURCHASABLE);
        }
        return e;
    }
}
