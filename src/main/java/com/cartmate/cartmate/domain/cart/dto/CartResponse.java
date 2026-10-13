package com.cartmate.cartmate.domain.cart.dto;

import java.util.List;

public record CartResponse(
        Long cartId,
        List<CartItemResponse> items,
        long totalPrice
) {

    public static CartResponse of(Long cartId, List<CartItemResponse> items) {
        long totalPrice = items.stream()
                .filter(CartItemResponse::purchasable)
                .mapToLong(CartItemResponse::totalPrice)
                .sum();
        return new CartResponse(cartId, items, totalPrice);
    }

    public static CartResponse empty() {
        return new CartResponse(null, List.of(), 0L);
    }
}
