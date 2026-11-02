package com.cartmate.cartmate.boundedContext.cart.dto.matching;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record DemandGroupPurchaseOpenRequest(
        @NotBlank
        @Size(max = 50)
        String title,
        @Future
        LocalDateTime deadlineAt,
        @Min(2)
        @Max(1000)
        Integer paybackMinBuyers,
        @Min(1)
        @Max(100000)
        Long paybackAmount
) {
}
