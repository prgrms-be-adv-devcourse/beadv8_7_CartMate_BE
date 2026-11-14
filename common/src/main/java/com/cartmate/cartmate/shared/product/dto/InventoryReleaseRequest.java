package com.cartmate.cartmate.shared.product.dto;

import com.cartmate.cartmate.shared.order.OrderType;

// 재고 선점 해제 요청: 결제 실패 등으로 그 주문이 선점한 재고를 풀어준다(reserved_quantity 감소)
public record InventoryReleaseRequest(
        OrderType orderType,
        Long orderId
) {
}
