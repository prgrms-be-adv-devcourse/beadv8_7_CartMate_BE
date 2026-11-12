package com.cartmate.cartmate.boundedContext.orderService.abuse.dto;

import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseLevel;

import java.time.LocalDateTime;

public record AbuseMemberSummaryResponse(
        Long memberId,
        AbuseLevel currentLevel,
        boolean restricted,
        LocalDateTime restrictedUntil,
        LocalDateTime lastJudgedAt
) {
}
