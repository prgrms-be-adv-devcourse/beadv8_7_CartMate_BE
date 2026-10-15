package com.cartmate.cartmate.domain.cart.groupshared.dto;

import com.cartmate.cartmate.domain.cart.groupshared.entity.SharedGroupPurchase;
import com.cartmate.cartmate.domain.cart.groupshared.entity.SharedGroupPurchaseStatus;

import java.time.LocalDateTime;


public record SharedGroupPurchaseSummaryResponse(
        Long id,
        String title,
        SharedGroupPurchaseStatus status,
        boolean creator,
        int participantCount,
        int maxParticipants,
        boolean editable,
        LocalDateTime editDeadlineAt
) {

    public static SharedGroupPurchaseSummaryResponse of(SharedGroupPurchase purchase, Long viewerId, LocalDateTime now) {
        return new SharedGroupPurchaseSummaryResponse(
                purchase.getId(),
                purchase.getTitle(),
                purchase.getStatus(),
                purchase.isCreator(viewerId),
                purchase.getMembers().size(),
                purchase.getMaxParticipants(),
                purchase.isEditable(now),
                purchase.getEditDeadlineAt()
        );
    }
}
