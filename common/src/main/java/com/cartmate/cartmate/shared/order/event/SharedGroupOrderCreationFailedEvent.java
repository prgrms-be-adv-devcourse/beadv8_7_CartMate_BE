package com.cartmate.cartmate.shared.order.event;

import com.cartmate.cartmate.global.event.DomainEvent;

public record SharedGroupOrderCreationFailedEvent(
        Long sharedGroupPurchaseId,
        String reason
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
