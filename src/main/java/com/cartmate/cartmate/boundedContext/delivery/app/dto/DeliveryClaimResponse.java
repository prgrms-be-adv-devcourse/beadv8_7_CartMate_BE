package com.cartmate.cartmate.boundedContext.delivery.app.dto;

import com.cartmate.cartmate.boundedContext.delivery.domain.ClaimReason;
import com.cartmate.cartmate.boundedContext.delivery.domain.ClaimStatus;
import com.cartmate.cartmate.boundedContext.delivery.domain.ClaimType;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryClaim;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryClaimItem;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

public record DeliveryClaimResponse(
        Long claimId,
        Long deliveryId,
        ClaimType claimType,
        ClaimReason reason,
        String detail,
        ClaimStatus status,
        LocalDateTime createdAt,
        List<Item> items
) {
    public static DeliveryClaimResponse from(DeliveryClaim claim, List<DeliveryClaimItem> claimItems) {
        return new DeliveryClaimResponse(
                claim.getId(),
                claim.getDelivery().getId(),
                claim.getClaimType(),
                claim.getReason(),
                claim.getDetail(),
                claim.getStatus(),
                claim.getCreatedAt(),
                claimItems.stream().map(Item::from).toList()
        );
    }

    @Schema(name = "DeliveryClaimItemResponse")
    public record Item(
            Long deliveryItemId,
            Long orderItemId,
            String productName,
            Long quantity
    ) {
        public static Item from(DeliveryClaimItem claimItem) {
            return new Item(
                    claimItem.getDeliveryItem().getId(),
                    claimItem.getDeliveryItem().getOrderItemId(),
                    claimItem.getDeliveryItem().getProductName(),
                    claimItem.getQuantity()
            );
        }
    }
}
