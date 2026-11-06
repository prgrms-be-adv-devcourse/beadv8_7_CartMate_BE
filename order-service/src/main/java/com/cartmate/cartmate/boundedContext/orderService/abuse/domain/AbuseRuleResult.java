package com.cartmate.cartmate.boundedContext.orderService.abuse.domain;

public record AbuseRuleResult(
        String ruleCode,
        long signalCount,
        AbuseLevel level,
        int score
) {
}
