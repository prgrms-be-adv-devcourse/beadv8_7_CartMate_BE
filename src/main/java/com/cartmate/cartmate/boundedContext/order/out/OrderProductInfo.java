package com.cartmate.cartmate.boundedContext.order.out;

public record OrderProductInfo(
        Long inventoryId,
        Long productId,
        Long sellerId,
        String productName,
        String optionName,
        long price,
        String imageUrl,
        boolean purchasable
) {
}
