package com.cartmate.cartmate.domain.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CartItemAddRequest(
        @NotNull Long inventoryId,
        @Min(1) @Max(999) int quantity
) {
}
