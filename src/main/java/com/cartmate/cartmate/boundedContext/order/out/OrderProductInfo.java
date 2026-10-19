package com.cartmate.cartmate.boundedContext.order.out;

public record OrderProductInfo(
        Long inventoryId,
        Long productId,
        String productName,
        String optionName,
        long price,
        String imageUrl
) {
}
