package com.cartmate.cartmate.boundedContext.orderService.abuse.app;

import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseLevel;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseRuleResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SharedOrderPaymentExpiredRuleTest {

    private final SharedOrderPaymentExpiredRule rule = new SharedOrderPaymentExpiredRule();

    @ParameterizedTest(name = "30일 안 {0}회 → {1}점, 이 규칙만으로 {2}")
    @CsvSource({"0, 0, NONE", "1, 34, CAUTION", "2, 68, SUSPICIOUS", "3, 102, RESTRICTED", "4, 136, RESTRICTED"})
    @DisplayName("공동물품 결제 기한 만료는 30일 안 3회면 제한되도록 1회 34점(100 ÷ 3 올림)이다")
    void evaluate(long count, int score, AbuseLevel levelAlone) {
        AbuseRuleResult result = rule.evaluate(count, AbuseJudgeUseCase.SCORE_THRESHOLD);

        assertThat(result.ruleCode()).isEqualTo("SHARED_ORDER_PAYMENT_EXPIRED");
        assertThat(result.signalCount()).isEqualTo(count);
        assertThat(result.score()).isEqualTo(score);
        assertThat(AbuseJudgeUseCase.SCORE_THRESHOLD.levelOf(score)).isEqualTo(levelAlone);
        assertThat(rule.weight().window()).isEqualTo(Duration.ofDays(30));
    }
}
