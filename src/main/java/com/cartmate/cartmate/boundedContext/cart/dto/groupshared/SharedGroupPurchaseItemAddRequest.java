package com.cartmate.cartmate.boundedContext.cart.dto.groupshared;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SharedGroupPurchaseItemAddRequest(

        @NotNull
        Long inventoryId,

        @Min(1)
        @Max(999)
        int quantity
) {
}
