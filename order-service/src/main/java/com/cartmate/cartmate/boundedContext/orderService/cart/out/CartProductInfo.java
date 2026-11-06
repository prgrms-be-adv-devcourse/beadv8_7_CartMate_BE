package com.cartmate.cartmate.boundedContext.orderService.cart.out;

public record CartProductInfo(
        Long inventoryId,
        Long productId,
        String productName,
        String optionName,
        int price,
        String imageUrl,
        boolean purchasable
) {
}
