package com.cartmate.cartmate.boundedContext.orderService.shared.order.dto;

public record TeamDeliveryAddress(
        String receiverName,
        String receiverPhone,
        String zipCode,
        String address,
        String addressDetail
) {
}
