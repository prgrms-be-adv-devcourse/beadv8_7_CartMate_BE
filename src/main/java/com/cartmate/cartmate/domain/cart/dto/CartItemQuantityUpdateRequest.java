package com.cartmate.cartmate.domain.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record CartItemQuantityUpdateRequest(
        @Min(1) @Max(999) int quantity
) {
}
