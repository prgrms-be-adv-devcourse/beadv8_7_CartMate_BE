package com.cartmate.cartmate.boundedContext.etcService.abuse.domain;

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
        AbuseLevel highestRuleLevel = ruleResults.stream()
                .map(AbuseRuleResult::level)
                .reduce(AbuseLevel.NONE, AbuseLevel::max);
        return new AbuseVerdict(AbuseLevel.max(highestRuleLevel, scoreThreshold.levelOf(totalScore)), totalScore, ruleResults);
    }

    public boolean requiresRestriction() {
        return level == AbuseLevel.RESTRICTED;
    }
}
