package com.cartmate.cartmate.boundedContext.orderService.abuse.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AbuseRuleResult(
        String ruleCode,
        long signalCount,
        int score
) {
}
