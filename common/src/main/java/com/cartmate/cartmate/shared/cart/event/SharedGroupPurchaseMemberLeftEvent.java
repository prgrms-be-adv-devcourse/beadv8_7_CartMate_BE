package com.cartmate.cartmate.shared.cart.event;

import com.cartmate.cartmate.global.event.DomainEvent;

import java.time.LocalDateTime;

public record SharedGroupPurchaseMemberLeftEvent(
        Long sharedGroupPurchaseId,
        Long memberId,
        LocalDateTime joinedAt,
        LocalDateTime leftAt
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
