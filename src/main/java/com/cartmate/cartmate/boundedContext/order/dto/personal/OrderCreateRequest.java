package com.cartmate.cartmate.boundedContext.order.dto.personal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record OrderCreateRequest(
        @NotEmpty
        @Size(max = 100)
        List<@NotNull Long> cartItemIds,
        @NotNull
        @Valid
        DeliveryAddressRequest deliveryAddress
) {
}
