package com.cartmate.cartmate.shared.cart.event;

import com.cartmate.cartmate.global.event.DomainEvent;

import java.time.LocalDateTime;

public record SharedGroupPurchaseMemberJoinedEvent(
        Long sharedGroupPurchaseId,
        Long memberId,
        long activeParticipationCount,
        LocalDateTime joinedAt
) implements DomainEvent {

    @Override
    public String aggregateType() {
        return "SharedGroupPurchase";
    }

    @Override
    public String aggregateId() {
        return String.valueOf(sharedGroupPurchaseId);
    }
}
