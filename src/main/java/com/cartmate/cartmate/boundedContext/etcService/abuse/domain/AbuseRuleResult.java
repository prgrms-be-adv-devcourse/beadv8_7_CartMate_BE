package com.cartmate.cartmate.boundedContext.etcService.abuse.domain;

public record AbuseRuleResult(
        String ruleCode,
        long signalCount,
        AbuseLevel level,
        int score
) {
}
