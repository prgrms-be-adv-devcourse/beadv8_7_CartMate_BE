package com.cartmate.cartmate.shared.payment.event;

import com.cartmate.cartmate.global.event.DomainEvent;

public record ClaimRefundCompletedEvent(
        Long claimId,
        Long orderId,
        Long refundAmount
) implements DomainEvent {

    @Override
    public String aggregateType() {
        return "DeliveryClaim";
    }

    @Override
    public String aggregateId() {
        return String.valueOf(claimId);
    }
}
