package com.cartmate.cartmate.boundedContext.abuse.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AbuseSignalTest {

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 29, 12, 0);

    @Test
    @DisplayName("판정하면 단계·점수·규칙 결과·판정 시각이 신호에 남고 JUDGED가 된다")
    void recordsVerdict() {
        AbuseSignal signal = AbuseSignal.received(1L, AbuseSignalType.SHARED_ORDER_PAYMENT_EXPIRED, "100",
                OCCURRED_AT, OCCURRED_AT.plusSeconds(1));
        List<AbuseRuleResult> results = List.of(new AbuseRuleResult("A", 2, AbuseLevel.SUSPICIOUS, 80));
        LocalDateTime judgedAt = OCCURRED_AT.plusSeconds(2);

        signal.judge(new AbuseVerdict(AbuseLevel.SUSPICIOUS, 80, results), judgedAt);

        assertThat(signal.getStatus()).isEqualTo(AbuseSignalStatus.JUDGED);
        assertThat(signal.getLevel()).isEqualTo(AbuseLevel.SUSPICIOUS);
        assertThat(signal.getTotalScore()).isEqualTo(80);
        assertThat(signal.getRuleResults()).containsExactlyElementsOf(results);
        assertThat(signal.getJudgedAt()).isEqualTo(judgedAt);
        assertThat(signal.getUpdatedAt()).isEqualTo(judgedAt);
    }
}
