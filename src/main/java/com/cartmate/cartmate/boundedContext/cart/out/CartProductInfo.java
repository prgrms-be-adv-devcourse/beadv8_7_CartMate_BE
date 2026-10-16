package com.cartmate.cartmate.boundedContext.cart.out;

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
