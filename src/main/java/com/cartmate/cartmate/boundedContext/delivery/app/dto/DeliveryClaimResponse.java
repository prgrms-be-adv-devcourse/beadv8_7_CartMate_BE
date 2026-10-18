package com.cartmate.cartmate.boundedContext.delivery.app.dto;

import com.cartmate.cartmate.boundedContext.delivery.domain.ClaimReason;
import com.cartmate.cartmate.boundedContext.delivery.domain.ClaimStatus;
import com.cartmate.cartmate.boundedContext.delivery.domain.ClaimType;
import com.cartmate.cartmate.boundedContext.delivery.domain.DeliveryClaim;

import java.time.LocalDateTime;

public record DeliveryClaimResponse(
        Long claimId,
        Long deliveryId,
        ClaimType claimType,
        ClaimReason reason,
        String detail,
        ClaimStatus status,
        LocalDateTime createdAt
) {
    public static DeliveryClaimResponse from(DeliveryClaim claim) {
        return new DeliveryClaimResponse(
                claim.getId(),
                claim.getDelivery().getId(),
                claim.getClaimType(),
                claim.getReason(),
                claim.getDetail(),
                claim.getStatus(),
                claim.getCreatedAt()
        );
    }
}
