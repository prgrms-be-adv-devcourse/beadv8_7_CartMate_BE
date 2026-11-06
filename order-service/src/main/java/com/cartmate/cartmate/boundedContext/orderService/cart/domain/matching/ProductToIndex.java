package com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching;

public record ProductToIndex(
        Long productId,
        Long categoryId,
        String name
) {
}
