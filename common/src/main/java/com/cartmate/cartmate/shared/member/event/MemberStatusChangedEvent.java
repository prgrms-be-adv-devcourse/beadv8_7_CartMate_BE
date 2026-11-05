package com.cartmate.cartmate.shared.member.event;

import java.time.LocalDateTime;

// 회원 정지·해제 시 발행. product는 판매자 정지 시 판매글 숨김 처리에 사용
public record MemberStatusChangedEvent(
        Long memberId,
        String role,
        String status,              // ACTIVE, SUSPENDED
        LocalDateTime suspendedUntil // 정지일 때만, 영구면 null
) {
}