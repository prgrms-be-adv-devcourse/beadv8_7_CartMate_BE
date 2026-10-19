package com.cartmate.cartmate.boundedContext.delivery.app.dto;

import com.cartmate.cartmate.boundedContext.delivery.domain.ClaimReason;
import com.cartmate.cartmate.boundedContext.delivery.domain.ClaimType;

import java.util.List;

// 교환·환불 접수 요청. 구매자가 배송(묶음)에서 상품을 골라 유형과 사유를 보낸다.
public record DeliveryClaimCreateRequest(
        Long deliveryId,
        ClaimType claimType,
        ClaimReason reason,
        String detail,
        List<Item> items
) {
    public record Item(
            Long deliveryItemId,
            Long quantity
    ) {}

    public DeliveryClaimCreateCommand toCommand(Long memberId) {
        List<DeliveryClaimCreateCommand.Item> commandItems = items == null ? null : items.stream()
                .map(item -> item == null ? null
                        : new DeliveryClaimCreateCommand.Item(item.deliveryItemId(), item.quantity()))
                .toList();

        return new DeliveryClaimCreateCommand(deliveryId, memberId, claimType, reason, detail, commandItems);
    }
}
