package com.cartmate.cartmate.domain.cart.personal.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record CartItemQuantityUpdateRequest(
        @Min(1) @Max(999) int quantity
) {
}
