package com.cartmate.cartmate.shared.order.dto;

public record DeliveryAddressInfo(
        String receiverName,
        String receiverPhone,
        String zipCode,
        String address,
        String addressDetail
) {
}
