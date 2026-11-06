package com.cartmate.cartmate.shared.cart.event;

import com.cartmate.cartmate.shared.cart.IndividualGroupPurchaseOrigin;

import com.cartmate.cartmate.shared.cart.IndividualGroupPurchaseCloseReason;

import com.cartmate.cartmate.global.event.DomainEvent;

import java.time.LocalDateTime;

public record IndividualGroupPurchaseClosedEvent(
        Long individualGroupPurchaseId,
        IndividualGroupPurchaseOrigin origin,
        IndividualGroupPurchaseCloseReason closeReason,
        LocalDateTime deadlineAt,
        LocalDateTime closedAt,
        long revision,
        Integer paybackMinBuyers,
        Long paybackAmount
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
