package com.cartmate.cartmate.boundedContext.cart.dto;

import jakarta.validation.constraints.NotNull;


public record IndividualGroupPurchaseProductAddRequest(

        @NotNull
        Long productId
) {
}
