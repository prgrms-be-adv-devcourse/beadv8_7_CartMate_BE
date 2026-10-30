package com.cartmate.cartmate.boundedContext.abuse.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AbuseJudgmentTest {

    private static final AbuseScoreThreshold SCORE_THRESHOLD = new AbuseScoreThreshold(30, 60, 100);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 28, 12, 0);
    private static final AbuseSignalCommand SIGNAL =
            new AbuseSignalCommand(1L, AbuseSignalType.SHARED_ORDER_PAYMENT_EXPIRED, "100", NOW);

    @Test
    @DisplayName("규칙 단계가 점수 단계보다 높으면 규칙 단계가 최종 단계다")
    void ruleLevelWins() {
        AbuseJudgment judgment = AbuseJudgment.judge(SIGNAL,
                List.of(new AbuseRuleResult("A", 3, AbuseLevel.RESTRICTED, 10)), SCORE_THRESHOLD, NOW);

        assertThat(judgment.getLevel()).isEqualTo(AbuseLevel.RESTRICTED);
        assertThat(judgment.getTotalScore()).isEqualTo(10);
        assertThat(judgment.requiresRestriction()).isTrue();
    }

    @Test
    @DisplayName("규칙마다 약해도 점수 합계가 기준을 넘으면 점수 단계가 최종 단계다")
    void scoreLevelWins() {
        AbuseJudgment judgment = AbuseJudgment.judge(SIGNAL, List.of(
                new AbuseRuleResult("A", 1, AbuseLevel.CAUTION, 40),
                new AbuseRuleResult("B", 1, AbuseLevel.CAUTION, 30)
        ), SCORE_THRESHOLD, NOW);

        assertThat(judgment.getTotalScore()).isEqualTo(70);
        assertThat(judgment.getLevel()).isEqualTo(AbuseLevel.SUSPICIOUS);
    }

    @Test
    @DisplayName("판정에는 신호 정보와 규칙별 결과가 남는다")
    void keepsReasons() {
        List<AbuseRuleResult> results = List.of(new AbuseRuleResult("A", 2, AbuseLevel.SUSPICIOUS, 80));

        AbuseJudgment judgment = AbuseJudgment.judge(SIGNAL, results, SCORE_THRESHOLD, NOW);

        assertThat(judgment.getMemberId()).isEqualTo(1L);
        assertThat(judgment.getSignalType()).isEqualTo(AbuseSignalType.SHARED_ORDER_PAYMENT_EXPIRED);
        assertThat(judgment.getSourceKey()).isEqualTo("100");
        assertThat(judgment.getJudgedAt()).isEqualTo(NOW);
        assertThat(judgment.getRuleResults()).containsExactlyElementsOf(results);
    }

    @Test
    @DisplayName("걸린 규칙이 없으면 NONE이다")
    void none() {
        AbuseJudgment judgment = AbuseJudgment.judge(SIGNAL,
                List.of(new AbuseRuleResult("A", 0, AbuseLevel.NONE, 0)), SCORE_THRESHOLD, NOW);

        assertThat(judgment.isNone()).isTrue();
        assertThat(judgment.requiresRestriction()).isFalse();
    }
}
