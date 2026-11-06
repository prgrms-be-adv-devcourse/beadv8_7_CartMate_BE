package com.cartmate.cartmate.shared.wallet.event;

import com.cartmate.cartmate.global.event.DomainEvent;

import java.time.LocalDateTime;

public record PaybackDepositFailedEvent(
        Long paybackId,
        Long memberId,
        String failureCode,
        boolean retryable,
        int attempt,
        LocalDateTime failedAt
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
