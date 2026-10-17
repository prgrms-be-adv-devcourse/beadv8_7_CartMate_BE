package com.cartmate.cartmate.boundedContext.delivery.app.dto;

import com.cartmate.cartmate.boundedContext.delivery.domain.PurchaseType;

import java.util.List;

public record DeliveryCreateCommand(
        Long memberId,
        Long orderId,
        PurchaseType purchaseType,
        String receiverName,
        String receiverPhone,
        String zipCode,
        String address,
        String addressDetail,
        List<Item> deliveryItem
) {
    public record Item(
            Long orderItemId,
            Long quantity
    ) {}
}
