package com.cartmate.cartmate.boundedContext.orderService.abuse.domain;

import java.util.List;

public record AbuseVerdict(
        AbuseLevel level,
        int totalScore,
        List<AbuseRuleResult> ruleResults
) {

    public AbuseVerdict {
        ruleResults = List.copyOf(ruleResults);
    }

    public static AbuseVerdict of(List<AbuseRuleResult> ruleResults, AbuseScoreThreshold scoreThreshold) {
        int totalScore = ruleResults.stream().mapToInt(AbuseRuleResult::score).sum();
        return new AbuseVerdict(scoreThreshold.levelOf(totalScore), totalScore, ruleResults);
    }

    public boolean requiresRestriction() {
        return level == AbuseLevel.RESTRICTED;
    }
}
