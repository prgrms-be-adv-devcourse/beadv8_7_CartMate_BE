package com.cartmate.cartmate.boundedContext.member.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AdminDashboardResponse(
        AdminMemberStats members,
        AdminPaymentStats payments,
        List<AdminRecentMember> recentMembers,
        LocalDateTime generatedAt
) {
    public record AdminMemberStats(long total, long todaySignups, long buyers, long sellers, long suspended) {}

    public record AdminPaymentStats(long todayPaidCount, long todayPaidAmount, long todayRefundCount, long todayRefundAmount) {}

    public record AdminRecentMember(Long id, String email, String nickname, String role, String status, LocalDateTime joinedAt) {}
}