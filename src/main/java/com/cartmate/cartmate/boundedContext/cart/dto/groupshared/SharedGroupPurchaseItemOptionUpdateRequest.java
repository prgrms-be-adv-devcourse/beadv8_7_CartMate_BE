package com.cartmate.cartmate.boundedContext.cart.dto.groupshared;

import jakarta.validation.constraints.NotNull;

public record SharedGroupPurchaseItemOptionUpdateRequest(

        @NotNull
        Long inventoryId
) {
}
