package com.cartmate.cartmate.domain.cart.groupindividual.dto;

import jakarta.validation.constraints.NotNull;


public record IndividualGroupPurchaseProductAddRequest(

        @NotNull
        Long productId
) {
}
