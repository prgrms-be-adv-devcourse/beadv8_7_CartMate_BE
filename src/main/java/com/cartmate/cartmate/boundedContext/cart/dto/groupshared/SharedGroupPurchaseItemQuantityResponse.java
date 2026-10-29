package com.cartmate.cartmate.boundedContext.cart.dto.groupshared;

import com.cartmate.cartmate.boundedContext.cart.domain.groupshared.SharedGroupPurchaseItem;

public record SharedGroupPurchaseItemQuantityResponse(
        Long itemId,
        int quantity
) {

    public static SharedGroupPurchaseItemQuantityResponse from(SharedGroupPurchaseItem item) {
        return new SharedGroupPurchaseItemQuantityResponse(item.getId(), item.getQuantity());
    }
}
