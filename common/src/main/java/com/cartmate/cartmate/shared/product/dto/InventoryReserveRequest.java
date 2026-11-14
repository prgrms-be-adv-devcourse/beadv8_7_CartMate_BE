package com.cartmate.cartmate.shared.product.dto;

import com.cartmate.cartmate.shared.order.OrderType;

import java.util.List;

// 재고 선점 요청: 결제 직전에 주문이 담은 재고를 수량만큼 잡아둔다(reserved_quantity 증가)
// 주문 하나의 재고는 전부 선점되거나 하나도 선점되지 않는다
public record InventoryReserveRequest(
        OrderType orderType,
        Long orderId,
        List<Item> items
) {
    public record Item(
            Long inventoryId,
            int quantity
    ) {
    }
}
