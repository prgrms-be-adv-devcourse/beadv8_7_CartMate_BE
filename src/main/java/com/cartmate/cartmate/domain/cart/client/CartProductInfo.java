package com.cartmate.cartmate.domain.cart.client;

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
