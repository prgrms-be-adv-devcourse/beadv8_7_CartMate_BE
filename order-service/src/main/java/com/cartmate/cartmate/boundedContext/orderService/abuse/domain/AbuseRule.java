package com.cartmate.cartmate.boundedContext.orderService.abuse.domain;

public interface AbuseRule {

    String code();

    AbuseSignalType signalType();

    AbuseRuleWeight weight();

    default AbuseRuleResult evaluate(long signalCount, AbuseScoreThreshold scoreThreshold) {
        return new AbuseRuleResult(code(), signalCount, weight().scoreOf(signalCount, scoreThreshold));
    }
}
