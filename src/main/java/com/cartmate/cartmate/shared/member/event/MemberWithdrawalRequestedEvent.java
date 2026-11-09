package com.cartmate.cartmate.shared.member.event;

import java.time.LocalDateTime;

/**
 * 회원이 탈퇴를 신청했다 (탈퇴 대기 시작).
 *
 * @param sellerId 판매자면 판매자 ID, 아니면 null. post가 받아 판매글을 전부 판매 중지한다.
 */
public record MemberWithdrawalRequestedEvent(
        Long memberId,
        Long sellerId,
        LocalDateTime requestedAt
) {
}