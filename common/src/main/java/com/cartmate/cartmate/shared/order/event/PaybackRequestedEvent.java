package com.cartmate.cartmate.shared.order.event;

import com.cartmate.cartmate.global.event.DomainEvent;

import java.time.LocalDateTime;

/**
 * order → wallet: 페이백을 달성한 개인물품 공동구매의 구매자 1명에게 예치금을 적립해 달라는 요청(MAT-12).
 * wallet은 paybackId로 한 번만 적립하고 결과를 {@code PaybackDepositedEvent} 또는 {@code PaybackDepositFailedEvent}로 돌려준다.
 * 재요청(자동 · 어드민)도 같은 paybackId로 오고 attempt만 늘어난다. 이미 적립한 paybackId면 다시 적립하지 말고
 * {@code PaybackDepositedEvent}를 다시 보낸다(order가 앞선 성공을 받지 못했을 수 있다).
 * 결과 이벤트에는 받은 attempt를 그대로 담는다. 키는 memberId — 한 회원의 지갑 변경이 순서대로 처리되게 한다.
 */
public record PaybackRequestedEvent(
        Long paybackId,
        Long memberId,
        long amount,
        Long individualGroupPurchaseId,
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
