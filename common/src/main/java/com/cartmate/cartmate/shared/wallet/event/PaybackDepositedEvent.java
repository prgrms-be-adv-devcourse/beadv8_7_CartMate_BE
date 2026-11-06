package com.cartmate.cartmate.shared.wallet.event;

import com.cartmate.cartmate.global.event.DomainEvent;

import java.time.LocalDateTime;

public record PaybackDepositedEvent(
        Long paybackId,
        Long memberId,
        long amount,
        int attempt,
        LocalDateTime depositedAt
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
