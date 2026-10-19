package com.cartmate.cartmate.boundedContext.order.dto.personal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record IndividualGroupOrderCreateRequest(
        @NotNull
        Long individualGroupPurchaseId,
        @NotEmpty
        @Size(max = 100)
        List<@NotNull @Valid Item> items,
        @NotNull
        @Valid
        DeliveryAddressRequest deliveryAddress
) {

    public record Item(
            @NotNull
            Long inventoryId,
            @Min(1)
            @Max(999)
            int quantity
    ) {
    }
}
