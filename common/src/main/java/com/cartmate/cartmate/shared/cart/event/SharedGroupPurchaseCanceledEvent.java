package com.cartmate.cartmate.shared.cart.event;

import com.cartmate.cartmate.global.event.DomainEvent;
import com.cartmate.cartmate.shared.cart.SharedGroupPurchaseCancelReason;

import java.time.LocalDateTime;
import java.util.List;

public record SharedGroupPurchaseCanceledEvent(
        Long sharedGroupPurchaseId,
        Long creatorMemberId,
        List<Long> participantMemberIds,
        SharedGroupPurchaseCancelReason cancelReason,
        LocalDateTime canceledAt
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
