package com.cartmate.cartmate.boundedContext.orderService.abuse.dto;

import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseAdminActionType;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseIdentityType;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseLevel;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseRestrictionSource;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignalStatus;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignalType;

import java.time.LocalDateTime;
import java.util.List;

public record AbuseMemberCaseResponse(
        Long memberId,
        AbuseLevel currentLevel,
        boolean restricted,
        LocalDateTime restrictedUntil,
        LocalDateTime lastJudgedAt,
        LocalDateTime scoreResetAt,
        Integer latestTotalScore,
        List<RuleScore> latestRuleScores,
        List<Signal> signals,
        List<Restriction> restrictions,
        List<Link> links,
        List<RingMember> ring,
        List<AdminAction> adminActions
) {

    public record RuleScore(String ruleCode, long signalCount, int score) {
    }

    public record Signal(
            Long id,
            AbuseSignalType signalType,
            String sourceKey,
            String contextKey,
            LocalDateTime occurredAt,
            AbuseSignalStatus status,
            AbuseLevel level,
            Integer totalScore,
            int attemptCount,
            String lastError
    ) {
    }

    public record Restriction(
            Long id,
            LocalDateTime startedAt,
            LocalDateTime endsAt,
            int extendedCount,
            Long lastSignalId,
            AbuseRestrictionSource source,
            LocalDateTime releasedAt,
            boolean active
    ) {
    }

    public record Link(
            Long memberId,
            AbuseIdentityType identityType,
            int sharedContextCount,
            LocalDateTime confirmedAt
    ) {
    }

    public record RingMember(
            Long memberId,
            AbuseLevel currentLevel,
            LocalDateTime restrictedUntil
    ) {
    }

    public record AdminAction(
            Long id,
            AbuseAdminActionType actionType,
            Long adminMemberId,
            String reason,
            Long restrictionId,
            Integer affectedSignalCount,
            LocalDateTime createdAt
    ) {
    }
}
