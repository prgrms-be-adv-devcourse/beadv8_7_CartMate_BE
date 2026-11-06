package com.cartmate.cartmate.shared.order.event;

import com.cartmate.cartmate.global.event.DomainEvent;
import com.cartmate.cartmate.shared.order.GroupPurchaseType;

import java.time.LocalDateTime;

public record PaybackRequestedEvent(
        Long paybackId,
        Long memberId,
        long amount,
        GroupPurchaseType groupPurchaseType,
        Long groupPurchaseId,
        int attempt,
        LocalDateTime requestedAt
) implements DomainEvent {

    @Override
    public String aggregateType() {
        return "MemberPayback";
    }

    @Override
    public String aggregateId() {
        return String.valueOf(memberId);
    }
}
