package com.cartmate.cartmate.boundedContext.cart.dto.groupindividual;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;


public record IndividualGroupPurchaseDeadlineUpdateRequest(
        @NotNull
        @Future
        LocalDateTime deadlineAt
) {
}
