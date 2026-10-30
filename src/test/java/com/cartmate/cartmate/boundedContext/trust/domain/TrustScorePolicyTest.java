package com.cartmate.cartmate.boundedContext.trust.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TrustScorePolicyTest {

    @Test
    @DisplayName("구매확정이 최소 표본 수 미만이면 점수 없이 NEW 등급이 된다")
    void newWhenSampleIsInsufficient() {
        TrustEvaluation evaluation = TrustScorePolicy.evaluate(new TrustMetrics(4, 0));

        assertThat(evaluation.score()).isNull();
        assertThat(evaluation.level()).isEqualTo(TrustLevel.NEW);
    }

    @Test
    @DisplayName("표본이 부족하면 귀책 클레임이 많아도 NEW 등급이다")
    void newIgnoresFaultClaims() {
        TrustEvaluation evaluation = TrustScorePolicy.evaluate(new TrustMetrics(4, 4));

        assertThat(evaluation.score()).isNull();
        assertThat(evaluation.level()).isEqualTo(TrustLevel.NEW);
    }

    @Test
    @DisplayName("구매확정이 최소 표본 수이고 귀책 클레임이 없으면 만점 HIGH 등급이다")
    void perfectScoreAtMinimumSample() {
        TrustEvaluation evaluation = TrustScorePolicy.evaluate(new TrustMetrics(5, 0));

        assertThat(evaluation.score()).isEqualTo(100);
        assertThat(evaluation.level()).isEqualTo(TrustLevel.HIGH);
    }

    @ParameterizedTest(name = "구매확정 {0}건, 귀책 클레임 {1}건이면 {2}점 {3} 등급이다")
    @CsvSource({
            "100, 0, 100, HIGH",
            "100, 5, 90, HIGH",
            "100, 6, 88, NORMAL",
            "100, 15, 70, NORMAL",
            "100, 16, 68, LOW",
            "100, 50, 0, LOW"
    })
    @DisplayName("귀책 클레임 비율에 따라 점수와 등급이 정해진다")
    void scoreAndLevel(int confirmedCount, int faultClaimCount, int expectedScore, TrustLevel expectedLevel) {
        TrustEvaluation evaluation = TrustScorePolicy.evaluate(new TrustMetrics(confirmedCount, faultClaimCount));

        assertThat(evaluation.score()).isEqualTo(expectedScore);
        assertThat(evaluation.level()).isEqualTo(expectedLevel);
    }

    @Test
    @DisplayName("귀책 클레임 비율이 50%를 넘어도 점수는 0점 아래로 내려가지 않는다")
    void scoreIsNotBelowZero() {
        TrustEvaluation evaluation = TrustScorePolicy.evaluate(new TrustMetrics(10, 9));

        assertThat(evaluation.score()).isZero();
        assertThat(evaluation.level()).isEqualTo(TrustLevel.LOW);
    }

    @Test
    @DisplayName("귀책 클레임이 구매확정보다 많아도 비율은 100%로 보정되어 0점이 된다")
    void faultRateIsCappedAtOne() {
        TrustEvaluation evaluation = TrustScorePolicy.evaluate(new TrustMetrics(5, 10));

        assertThat(evaluation.score()).isZero();
        assertThat(evaluation.level()).isEqualTo(TrustLevel.LOW);
    }
}
