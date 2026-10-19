package com.cartmate.cartmate.shared.cart.dto;

public record CartOrderItem(
        Long cartItemId,
        Long productId,
        Long inventoryId,
        int quantity
) {
}
