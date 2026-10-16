package com.cartmate.cartmate.boundedContext.member.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AdminMemberDetailResponse(
        Long id,
        String email,
        String nickname,
        String phone,
        LocalDate birthDate,
        String role,
        String status,
        LocalDateTime joinedAt,
        long walletBalance,
        int activeGroupCount,
        int recentOrderCount,
        Suspension currentSuspension,
        List<Suspension> suspensions
) {
    public record Suspension(
            Long id,
            String reasonType,
            String reasonDetail,
            LocalDateTime suspendedAt,
            LocalDateTime suspendedUntil,
            LocalDateTime releasedAt,
            String releaseType,
            Long adminId,
            String adminNickname
    ) {
    }
}