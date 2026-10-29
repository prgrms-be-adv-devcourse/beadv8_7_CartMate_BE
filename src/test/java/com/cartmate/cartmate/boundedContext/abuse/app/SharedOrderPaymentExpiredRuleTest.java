package com.cartmate.cartmate.boundedContext.abuse.app;

import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseLevel;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseRuleResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SharedOrderPaymentExpiredRuleTest {

    private final SharedOrderPaymentExpiredRule rule = new SharedOrderPaymentExpiredRule();

    @ParameterizedTest(name = "30일 안 {0}회 → {1}, {2}점")
    @CsvSource({"0, NONE, 0", "1, CAUTION, 40", "2, SUSPICIOUS, 80", "3, RESTRICTED, 120", "4, RESTRICTED, 160"})
    @DisplayName("공동물품 결제 방해는 30일 안 1/2/3회에 1/2/3단계, 1회 40점이다")
    void evaluate(long count, AbuseLevel level, int score) {
        AbuseRuleResult result = rule.evaluate(count);

        assertThat(result.ruleCode()).isEqualTo("SHARED_ORDER_PAYMENT_EXPIRED");
        assertThat(result.signalCount()).isEqualTo(count);
        assertThat(result.level()).isEqualTo(level);
        assertThat(result.score()).isEqualTo(score);
        assertThat(rule.threshold().window()).isEqualTo(Duration.ofDays(30));
    }
}
