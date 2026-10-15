package com.cartmate.cartmate.domain.cart.groupshared.dto;

import jakarta.validation.constraints.NotNull;

public record SharedGroupPurchaseItemOptionUpdateRequest(

        @NotNull
        Long inventoryId
) {
}
