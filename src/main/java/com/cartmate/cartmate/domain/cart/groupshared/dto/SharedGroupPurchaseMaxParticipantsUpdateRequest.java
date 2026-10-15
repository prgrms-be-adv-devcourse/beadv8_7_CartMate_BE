package com.cartmate.cartmate.domain.cart.groupshared.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record SharedGroupPurchaseMaxParticipantsUpdateRequest(

        @Min(2)
        @Max(100)
        int maxParticipants
) {
}
