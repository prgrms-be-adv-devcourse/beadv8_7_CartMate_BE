package com.cartmate.cartmate.boundedContext.cart.dto.groupshared;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SharedGroupPurchaseCreateRequest(

        @NotBlank
        @Size(max = 50)
        String title,

        @Min(2)
        @Max(100)
        int maxParticipants
) {
}
