package com.cartmate.cartmate.boundedContext.etcService.delivery.app.dto;

import com.cartmate.cartmate.boundedContext.etcService.delivery.domain.ClaimReason;
import com.cartmate.cartmate.boundedContext.etcService.delivery.domain.ClaimType;

import java.util.List;

public record DeliveryClaimCreateCommand(
        Long deliveryId,
        Long memberId,
        ClaimType claimType,
        ClaimReason reason,
        String detail,
        List<Item> items
) {
    public record Item(
            Long deliveryItemId,
            Long quantity
    ) {}
}
