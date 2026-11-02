package com.cartmate.cartmate.boundedContext.abuse.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AbuseVerdictTest {

    private static final AbuseScoreThreshold SCORE_THRESHOLD = new AbuseScoreThreshold(30, 60, 100);

    @Test
    @DisplayName("규칙 단계가 점수 단계보다 높으면 규칙 단계가 최종 단계다")
    void ruleLevelWins() {
        AbuseVerdict verdict = AbuseVerdict.of(
                List.of(new AbuseRuleResult("A", 3, AbuseLevel.RESTRICTED, 10)), SCORE_THRESHOLD);

        assertThat(verdict.level()).isEqualTo(AbuseLevel.RESTRICTED);
        assertThat(verdict.totalScore()).isEqualTo(10);
        assertThat(verdict.requiresRestriction()).isTrue();
    }

    @Test
    @DisplayName("규칙마다 약해도 점수 합계가 기준을 넘으면 점수 단계가 최종 단계다")
    void scoreLevelWins() {
        AbuseVerdict verdict = AbuseVerdict.of(List.of(
                new AbuseRuleResult("A", 1, AbuseLevel.CAUTION, 40),
                new AbuseRuleResult("B", 1, AbuseLevel.CAUTION, 30)
        ), SCORE_THRESHOLD);

        assertThat(verdict.totalScore()).isEqualTo(70);
        assertThat(verdict.level()).isEqualTo(AbuseLevel.SUSPICIOUS);
        assertThat(verdict.requiresRestriction()).isFalse();
    }

    @Test
    @DisplayName("걸린 규칙이 없으면 NONE이고 규칙별 결과는 그대로 남는다")
    void none() {
        List<AbuseRuleResult> results = List.of(new AbuseRuleResult("A", 0, AbuseLevel.NONE, 0));

        AbuseVerdict verdict = AbuseVerdict.of(results, SCORE_THRESHOLD);

        assertThat(verdict.level()).isEqualTo(AbuseLevel.NONE);
        assertThat(verdict.ruleResults()).containsExactlyElementsOf(results);
    }
}
