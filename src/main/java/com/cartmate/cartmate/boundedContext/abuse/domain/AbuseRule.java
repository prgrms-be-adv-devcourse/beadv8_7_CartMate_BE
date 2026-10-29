package com.cartmate.cartmate.boundedContext.abuse.domain;

public interface AbuseRule {

    String code();

    AbuseSignalType signalType();

    AbuseRuleThreshold threshold();

    default AbuseRuleResult evaluate(long signalCount) {
        AbuseRuleThreshold threshold = threshold();
        return new AbuseRuleResult(code(), signalCount, threshold.levelOf(signalCount), threshold.scoreOf(signalCount));
    }
}
