package com.cartmate.cartmate.boundedContext.cart.dto.groupshared;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record SharedGroupPurchaseItemQuantityUpdateRequest(

        @Min(1)
        @Max(999)
        int quantity
) {
}
