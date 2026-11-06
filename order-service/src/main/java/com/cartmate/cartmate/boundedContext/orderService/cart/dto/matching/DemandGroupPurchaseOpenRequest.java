package com.cartmate.cartmate.boundedContext.orderService.cart.dto.matching;

import com.cartmate.cartmate.shared.cart.PaybackOverlapPolicy;
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
        @Max(100000)
        Integer paybackMinQuantity,
        @Min(1)
        @Max(100000)
        Long paybackAmountPerUnit,
        PaybackOverlapPolicy paybackOverlapPolicy
) {
}
