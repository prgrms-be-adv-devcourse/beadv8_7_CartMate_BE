package com.cartmate.cartmate.shared.wallet.event;

import com.cartmate.cartmate.global.event.DomainEvent;

import java.time.LocalDateTime;

/**
 * wallet → order: 페이백 예치금 적립 완료(MAT-12 계약 초안, 발행은 wallet 담당).
 * 같은 paybackId에 두 번 와도 order는 한 번만 반영한다.
 */
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
