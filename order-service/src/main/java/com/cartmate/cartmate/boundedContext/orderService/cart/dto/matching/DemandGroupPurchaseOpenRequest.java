package com.cartmate.cartmate.boundedContext.orderService.cart.dto.matching;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record DemandGroupPurchaseOpenRequest(
        @NotBlank
        @Size(max = 50)
        String title,
        @Future
        LocalDateTime deadlineAt
) {
}
