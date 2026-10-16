package com.cartmate.cartmate.boundedContext.cart.dto.groupshared;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record SharedGroupPurchaseJoinRequest(

        @NotNull
        @Pattern(regexp = "^[a-z0-9]{12}$")
        String inviteCode
) {
}
