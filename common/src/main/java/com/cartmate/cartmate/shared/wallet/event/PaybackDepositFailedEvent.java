package com.cartmate.cartmate.shared.wallet.event;

import com.cartmate.cartmate.global.event.DomainEvent;

import java.time.LocalDateTime;

/**
 * wallet → order: 페이백 예치금 적립 실패(MAT-12 계약 초안, 발행은 wallet 담당).
 * 일시 오류는 wallet 쪽 Kafka 재시도 · DLT에 맡기고, 이 이벤트는 업무상 실패만 보낸다.
 * attempt = 받은 요청의 attempt. order는 지금 시도와 다른 실패(앞선 시도의 늦은 결과)를 무시한다.
 * retryable = 같은 요청을 다시 보내면 성공할 수 있다(예: 동시 변경 충돌). false = 다시 보내도 같다(예: 지갑 없음 · 탈퇴 회원).
 */
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
