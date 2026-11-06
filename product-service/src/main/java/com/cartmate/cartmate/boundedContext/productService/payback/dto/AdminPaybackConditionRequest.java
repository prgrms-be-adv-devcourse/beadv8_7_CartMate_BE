package com.cartmate.cartmate.boundedContext.productService.payback.dto;

import com.cartmate.cartmate.shared.product.PaybackOverlapPolicy;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AdminPaybackConditionRequest(
        @NotNull
        @Min(2)
        @Max(100000)
        Integer minQuantity,
        @NotNull
        @Min(1)
        @Max(100000)
        Long amountPerUnit,
        PaybackOverlapPolicy overlapPolicy
) {
}
