package com.cartmate.cartmate.shared.cart.event;

import com.cartmate.cartmate.global.event.DomainEvent;

import java.time.LocalDateTime;

public record DemandGroupPurchaseOpenedEvent(
        Long individualGroupPurchaseId,
        Long productId,
        String title,
        String inviteCode,
        LocalDateTime deadlineAt,
        int paybackMinBuyers,
        long paybackAmount,
        LocalDateTime openedAt
) implements DomainEvent {

    @Override
    public String aggregateType() {
        return "IndividualGroupPurchase";
    }

    @Override
    public String aggregateId() {
        return String.valueOf(individualGroupPurchaseId);
    }
}
