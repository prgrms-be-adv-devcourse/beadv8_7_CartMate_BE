package com.cartmate.cartmate.boundedContext.cart.dto.groupshared;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record SharedGroupPurchaseItemQuantityAdjustRequest(

        @Min(-998)
        @Max(998)
        int delta
) {
}
