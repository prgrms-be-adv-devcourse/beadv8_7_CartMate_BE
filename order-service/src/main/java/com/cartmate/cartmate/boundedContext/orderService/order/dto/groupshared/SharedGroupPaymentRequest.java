package com.cartmate.cartmate.boundedContext.orderService.order.dto.groupshared;

import com.cartmate.cartmate.shared.order.dto.TeamDeliveryAddress;

public record SharedGroupPaymentRequest(
        TeamDeliveryAddress teamDeliveryAddress
) {
}