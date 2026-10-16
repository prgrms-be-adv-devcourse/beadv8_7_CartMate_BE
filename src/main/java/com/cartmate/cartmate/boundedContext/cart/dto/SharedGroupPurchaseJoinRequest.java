package com.cartmate.cartmate.boundedContext.cart.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record SharedGroupPurchaseJoinRequest(

        @NotNull
        @Pattern(regexp = "^[a-z0-9]{12}$")
        String inviteCode
) {
}
