package com.cartmate.cartmate.boundedContext.member.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 탈퇴 가능 여부. 막는 이유가 있으면 화면에 그대로 보여 준다.
 *
 * @param status          지금 회원 상태 (ACTIVE / WITHDRAWAL_PENDING)
 * @param requestedAt     탈퇴 신청 시각 (대기 중일 때)
 * @param scheduledAt     탈퇴 확정 예정 시각 (대기 중일 때)
 */
public record WithdrawalCheckResponse(
        boolean canWithdraw,
        List<WithdrawalBlocker> blockers,
        String status,
        LocalDateTime requestedAt,
        LocalDateTime scheduledAt
) {
    // 탈퇴를 막는 이유 하나 (예: PAYMENT_PENDING_ORDERS, "결제 대기 중인 주문이 있습니다.", 2)
    public record WithdrawalBlocker(String code, String message, long count) {}
}