package com.cartmate.cartmate.boundedContext.orderService.shared.cart.dto;

public record CartOrderItem(
        Long cartItemId,
        Long productId,
        Long inventoryId,
        int quantity
) {
}
