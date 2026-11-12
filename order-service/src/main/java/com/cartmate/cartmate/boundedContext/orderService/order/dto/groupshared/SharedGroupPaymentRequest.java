package com.cartmate.cartmate.boundedContext.orderService.order.dto.groupshared;

import com.cartmate.cartmate.shared.order.dto.TeamDeliveryAddress;
import jakarta.validation.Valid;

public record SharedGroupPaymentRequest(
        @Valid
        TeamDeliveryAddress teamDeliveryAddress
) {
}
