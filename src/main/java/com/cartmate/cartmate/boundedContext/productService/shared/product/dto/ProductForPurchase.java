package com.cartmate.cartmate.boundedContext.productService.shared.product.dto;

public record ProductForPurchase(
        Long productId,
        Long sellerId,
        String name,
        Long basePrice,
        boolean purchasable,
        String imageUrl
) {
}
