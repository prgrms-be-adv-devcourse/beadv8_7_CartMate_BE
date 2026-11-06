package com.cartmate.cartmate.boundedContext.orderService.cart.out;

public record CartProductProfile(
        Long productId,
        String productName,
        Long categoryId
) {
}
