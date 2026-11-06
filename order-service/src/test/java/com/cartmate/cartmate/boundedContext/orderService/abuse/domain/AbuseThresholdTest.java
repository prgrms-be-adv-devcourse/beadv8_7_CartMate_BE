package com.cartmate.cartmate.boundedContext.orderService.abuse.domain;

import com.cartmate.cartmate.boundedContext.orderService.abuse.exception.AbuseErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbuseThresholdTest {

    private final AbuseScoreThreshold scoreThreshold = new AbuseScoreThreshold(30, 60, 100);

    @ParameterizedTest(name = "{0}점 → {1}")
    @CsvSource({"0, NONE", "29, NONE", "30, CAUTION", "59, CAUTION", "60, SUSPICIOUS", "99, SUSPICIOUS", "100, RESTRICTED"})
    @DisplayName("점수 기준은 합계 점수로 단계를 정한다")
    void scoreLevelOf(int score, AbuseLevel expected) {
        assertThat(scoreThreshold.levelOf(score)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "허용 {0}회 → 신호당 {1}점")
    @CsvSource({"3, 34", "4, 25", "5, 20", "8, 13", "10, 10"})
    @DisplayName("규칙 점수는 제한 기준선(100)을 허용 횟수로 나눈 값을 올림해, 허용 횟수째 신호에서 제한에 닿는다")
    void scorePerSignal(int restrictAfterCount, int expected) {
        AbuseRuleWeight weight = new AbuseRuleWeight(Duration.ofDays(30), restrictAfterCount);

        assertThat(weight.scorePerSignal(scoreThreshold)).isEqualTo(expected);
        assertThat(scoreThreshold.levelOf(weight.scoreOf(restrictAfterCount, scoreThreshold))).isEqualTo(AbuseLevel.RESTRICTED);
        assertThat(scoreThreshold.levelOf(weight.scoreOf(restrictAfterCount - 1, scoreThreshold))).isNotEqualTo(AbuseLevel.RESTRICTED);
    }

    @Test
    @DisplayName("기간이 0 이하이거나 허용 횟수가 1 미만이면 규칙 가중치를 만들 수 없다")
    void invalidRuleWeight() {
        assertThatThrownBy(() -> new AbuseRuleWeight(Duration.ZERO, 3))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(AbuseErrorCode.ABUSE_INVALID_THRESHOLD.getMsg());
        assertThatThrownBy(() -> new AbuseRuleWeight(Duration.ofDays(1), 0))
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
