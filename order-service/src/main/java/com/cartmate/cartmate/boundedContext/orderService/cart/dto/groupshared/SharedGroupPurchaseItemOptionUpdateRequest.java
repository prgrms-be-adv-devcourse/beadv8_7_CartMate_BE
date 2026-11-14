package com.cartmate.cartmate.boundedContext.orderService.cart.dto.groupshared;

import jakarta.validation.constraints.NotNull;

public record SharedGroupPurchaseItemOptionUpdateRequest(

        @NotNull
        Long inventoryId
) {
}
