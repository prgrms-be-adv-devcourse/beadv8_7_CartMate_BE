package com.cartmate.cartmate.boundedContext.orderService.abuse.app;

import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseLevel;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseRule;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseScoreThreshold;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignalType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AbuseRulesTest {

    private static final AbuseScoreThreshold THRESHOLD = AbuseJudgeUseCase.SCORE_THRESHOLD;

    private static final List<AbuseRule> RULES = List.of(
            new SharedOrderPaymentExpiredRule(),
            new SharedPurchaseCreatorCanceledRule(),
            new IndividualOrderPaymentExpiredRule(),
            new SharedPurchaseLeftRule(),
            new SharedPurchaseConcurrentJoinRule()
    );

    static Stream<Arguments> allowedCounts() {
        return Stream.of(
                Arguments.of(new SharedOrderPaymentExpiredRule(), 3, 34),
                Arguments.of(new SharedPurchaseCreatorCanceledRule(), 4, 25),
                Arguments.of(new IndividualOrderPaymentExpiredRule(), 5, 20),
                Arguments.of(new SharedPurchaseLeftRule(), 8, 13),
                Arguments.of(new SharedPurchaseConcurrentJoinRule(), 10, 10)
        );
    }

    @ParameterizedTest(name = "{0}: 30일 안 {1}회째에 제한, 신호당 {2}점")
    @MethodSource("allowedCounts")
    @DisplayName("행동별 허용 횟수째 신호에서 그 규칙만으로 제한 기준선에 닿는다")
    void reachesRestrictionAtAllowedCount(AbuseRule rule, int allowedCount, int scorePerSignal) {
        assertThat(rule.weight().window()).isEqualTo(Duration.ofDays(30));
        assertThat(rule.evaluate(1, THRESHOLD).score()).isEqualTo(scorePerSignal);
        assertThat(THRESHOLD.levelOf(rule.evaluate(allowedCount, THRESHOLD).score())).isEqualTo(AbuseLevel.RESTRICTED);
        assertThat(THRESHOLD.levelOf(rule.evaluate(allowedCount - 1, THRESHOLD).score())).isNotEqualTo(AbuseLevel.RESTRICTED);
    }

    @Test
    @DisplayName("신호 종류마다 규칙이 정확히 하나 있고 규칙 코드는 신호 종류 이름과 같다")
    void oneRulePerSignalType() {
        assertThat(RULES).extracting(AbuseRule::signalType)
                .containsExactlyInAnyOrderElementsOf(Arrays.asList(AbuseSignalType.values()));
        RULES.forEach(rule -> assertThat(rule.code()).isEqualTo(rule.signalType().name()));
    }
}
