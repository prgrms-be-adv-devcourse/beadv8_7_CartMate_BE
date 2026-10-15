package com.cartmate.cartmate.domain.cart.groupshared.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record SharedGroupPurchaseItemQuantityUpdateRequest(

        @Min(1)
        @Max(999)
        int quantity
) {
}
