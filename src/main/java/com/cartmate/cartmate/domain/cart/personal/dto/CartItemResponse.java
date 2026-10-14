package com.cartmate.cartmate.domain.cart.personal.dto;

import com.cartmate.cartmate.domain.cart.client.CartProductInfo;
import com.cartmate.cartmate.domain.cart.personal.entity.CartItem;

public record CartItemResponse(
        Long cartItemId,
        Long productId,
        Long inventoryId,
        String productName,
        String optionName,
        String imageUrl,
        int price,
        int quantity,
        long totalPrice,
        boolean purchasable
) {

    public static CartItemResponse of(CartItem item, CartProductInfo product) {
        if (product == null) {
            return new CartItemResponse(
                    item.getId(), item.getProductId(), item.getInventoryId(),
                    null, null, null, 0, item.getQuantity(), 0L, false
            );
        }
        return new CartItemResponse(
                item.getId(),
                item.getProductId(),
                item.getInventoryId(),
                product.productName(),
                product.optionName(),
                product.imageUrl(),
                product.price(),
                item.getQuantity(),
                (long) product.price() * item.getQuantity(),
                product.purchasable()
        );
    }
}
