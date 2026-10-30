package com.cartmate.cartmate.boundedContext.abuse.domain;

public record AbuseRuleResult(
        String ruleCode,
        long signalCount,
        AbuseLevel level,
        int score
) {
}
