package com.cartmate.cartmate.shared.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TeamDeliveryAddress(
        @NotBlank
        @Size(max = 50)
        String receiverName,
        @NotBlank
        @Size(max = 20)
        String receiverPhone,
        @NotBlank
        @Size(max = 10)
        String zipCode,
        @NotBlank
        @Size(max = 255)
        String address,
        @Size(max = 255)
        String addressDetail
) {
}
