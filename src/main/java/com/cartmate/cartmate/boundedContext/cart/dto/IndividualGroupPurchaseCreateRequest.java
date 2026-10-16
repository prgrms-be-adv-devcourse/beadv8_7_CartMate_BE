package com.cartmate.cartmate.boundedContext.cart.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record IndividualGroupPurchaseCreateRequest(
        @NotBlank
        @Size(max = 50)
        String title,
        @NotEmpty
        @Size(max = 20)
        List<@NotNull Long> productIds
) {
}
