package com.cartmate.cartmate.boundedContext.orderService.cart.dto.groupindividual;

import jakarta.validation.constraints.NotNull;


public record IndividualGroupPurchaseProductAddRequest(

        @NotNull
        Long productId
) {
}
