package com.cartmate.cartmate.boundedContext.orderService.order.out;

import com.cartmate.cartmate.shared.order.OrderType;
import com.cartmate.cartmate.shared.product.dto.InventoryReserveRequest;

import java.util.List;

// 상품 서버 재고 선점 · 선점 해제 (결제 직전 / 결제 실패)
public interface OrderInventoryClient {

    // 남은 수량이 부족하면 ORDER_OUT_OF_STOCK, 살 수 없는 재고면 ORDER_PRODUCT_NOT_PURCHASABLE
    void reserve(OrderType orderType, Long orderId, List<InventoryReserveRequest.Item> items);

    void release(OrderType orderType, Long orderId);
}
