package com.cartmate.cartmate.boundedContext.abuse.domain;

import com.cartmate.cartmate.boundedContext.abuse.exception.AbuseErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbuseThresholdTest {

    private final AbuseRuleThreshold ruleThreshold = new AbuseRuleThreshold(Duration.ofDays(30), 1, 2, 3, 40);
    private final AbuseScoreThreshold scoreThreshold = new AbuseScoreThreshold(30, 60, 100);

    @ParameterizedTest(name = "{0}회 → {1}")
    @CsvSource({"0, NONE", "1, CAUTION", "2, SUSPICIOUS", "3, RESTRICTED", "5, RESTRICTED"})
    @DisplayName("규칙 기준은 신호 수로 단계를 정한다")
    void ruleLevelOf(long count, AbuseLevel expected) {
        assertThat(ruleThreshold.levelOf(count)).isEqualTo(expected);
    }

    @Test
    @DisplayName("규칙 점수는 신호 수 × 건당 점수다")
    void ruleScoreOf() {
        assertThat(ruleThreshold.scoreOf(0)).isZero();
        assertThat(ruleThreshold.scoreOf(3)).isEqualTo(120);
    }

    @ParameterizedTest(name = "{0}점 → {1}")
    @CsvSource({"0, NONE", "29, NONE", "30, CAUTION", "59, CAUTION", "60, SUSPICIOUS", "99, SUSPICIOUS", "100, RESTRICTED"})
    @DisplayName("점수 기준은 합계 점수로 단계를 정한다")
    void scoreLevelOf(int score, AbuseLevel expected) {
        assertThat(scoreThreshold.levelOf(score)).isEqualTo(expected);
    }

    @Test
    @DisplayName("규칙 기준값이 오름차순이 아니거나 기간이 0 이하이면 만들 수 없다")
    void invalidRuleThreshold() {
        assertThatThrownBy(() -> new AbuseRuleThreshold(Duration.ZERO, 1, 2, 3, 40))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(AbuseErrorCode.ABUSE_INVALID_THRESHOLD.getMsg());
        assertThatThrownBy(() -> new AbuseRuleThreshold(Duration.ofDays(1), 2, 1, 3, 40))
                .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> new AbuseRuleThreshold(Duration.ofDays(1), 0, 1, 2, 40))
                .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> new AbuseRuleThreshold(Duration.ofDays(1), 1, 2, 3, -1))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("점수 기준값이 오름차순이 아니면 만들 수 없다")
    void invalidScoreThreshold() {
        assertThatThrownBy(() -> new AbuseScoreThreshold(30, 20, 100))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(AbuseErrorCode.ABUSE_INVALID_THRESHOLD.getMsg());
        assertThatThrownBy(() -> new AbuseScoreThreshold(0, 60, 100))
                .isInstanceOf(ServiceException.class);
    }
}
