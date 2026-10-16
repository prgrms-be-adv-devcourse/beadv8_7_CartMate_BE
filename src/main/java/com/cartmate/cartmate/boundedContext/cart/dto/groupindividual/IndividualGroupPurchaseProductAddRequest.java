package com.cartmate.cartmate.boundedContext.cart.dto.groupindividual;

import jakarta.validation.constraints.NotNull;


public record IndividualGroupPurchaseProductAddRequest(

        @NotNull
        Long productId
) {
}
