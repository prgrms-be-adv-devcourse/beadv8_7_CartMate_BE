package com.cartmate.cartmate.boundedContext.delivery.app.dto;

import com.cartmate.cartmate.boundedContext.delivery.domain.PurchaseType;

import java.util.List;

public record DeliveryCreateCommand(
        Long memberId,
        Long sellerId,
        Long orderId,
        PurchaseType purchaseType,
        String receiverName,
        String receiverPhone,
        String zipCode,
        String address,
        String addressDetail,
        String idempotencyKey,
        List<Item> deliveryItem
) {
    public record Item(
            Long orderItemId,
            String productName,
            Long unitPrice,     // 옵션 가격이 반영된 최종 단가
            Long quantity,
            List<Option> options
    ) {}

    public record Option(
            String optionName,
            String optionValue
    ) {}
}
