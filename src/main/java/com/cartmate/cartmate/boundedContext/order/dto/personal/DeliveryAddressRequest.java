package com.cartmate.cartmate.boundedContext.order.dto.personal;

import com.cartmate.cartmate.boundedContext.order.domain.DeliveryAddress;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeliveryAddressRequest(
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

    public DeliveryAddress toDeliveryAddress() {
        return DeliveryAddress.of(receiverName, receiverPhone, zipCode, address, addressDetail);
    }
}
