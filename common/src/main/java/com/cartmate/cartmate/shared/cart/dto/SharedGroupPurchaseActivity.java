package com.cartmate.cartmate.shared.cart.dto;

import com.cartmate.cartmate.shared.cart.SharedGroupPurchaseStatus;

import java.time.LocalDateTime;

public record SharedGroupPurchaseActivity(
        Long id,
        String title,
        SharedGroupPurchaseStatus status,
        int participantCount,
        int maxParticipants,
        LocalDateTime editDeadlineAt
) {
}
