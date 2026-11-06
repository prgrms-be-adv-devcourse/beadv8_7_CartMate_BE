package com.cartmate.cartmate.boundedContext.orderService.abuse.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AbuseVerdictTest {

    private static final AbuseScoreThreshold SCORE_THRESHOLD = new AbuseScoreThreshold(30, 60, 100);

    @Test
    @DisplayName("규칙 하나만으로 제한 기준선을 넘으면 제한이다")
    void singleRuleReachesRestriction() {
        AbuseVerdict verdict = AbuseVerdict.of(List.of(new AbuseRuleResult("A", 3, 102)), SCORE_THRESHOLD);

        assertThat(verdict.level()).isEqualTo(AbuseLevel.RESTRICTED);
        assertThat(verdict.totalScore()).isEqualTo(102);
        assertThat(verdict.requiresRestriction()).isTrue();
    }

    @Test
    @DisplayName("규칙마다 약해도 점수 합계로 단계가 정해진다")
    void totalScoreDecidesLevel() {
        AbuseVerdict verdict = AbuseVerdict.of(List.of(
                new AbuseRuleResult("A", 1, 34),
                new AbuseRuleResult("B", 3, 39),
                new AbuseRuleResult("C", 1, 25)
        ), SCORE_THRESHOLD);

        assertThat(verdict.totalScore()).isEqualTo(98);
        assertThat(verdict.level()).isEqualTo(AbuseLevel.SUSPICIOUS);
        assertThat(verdict.requiresRestriction()).isFalse();
    }

    @Test
    @DisplayName("걸린 규칙이 없으면 NONE이고 규칙별 결과는 그대로 남는다")
    void none() {
        List<AbuseRuleResult> results = List.of(new AbuseRuleResult("A", 0, 0));

        AbuseVerdict verdict = AbuseVerdict.of(results, SCORE_THRESHOLD);

        assertThat(verdict.level()).isEqualTo(AbuseLevel.NONE);
        assertThat(verdict.ruleResults()).containsExactlyElementsOf(results);
    }
}
