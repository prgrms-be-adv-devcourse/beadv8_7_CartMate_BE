package com.cartmate.cartmate.boundedContext.cart.dto.groupshared;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record SharedGroupPurchaseMaxParticipantsUpdateRequest(

        @Min(2)
        @Max(100)
        int maxParticipants
) {
}
