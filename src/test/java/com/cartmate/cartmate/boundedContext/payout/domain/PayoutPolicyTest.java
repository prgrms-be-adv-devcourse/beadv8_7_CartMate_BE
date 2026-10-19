package com.cartmate.cartmate.boundedContext.payout.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PayoutPolicyTest {

    @Test
    @DisplayName("수수료율은 3%이고 지급일은 15일이다")
    void constants() {
        assertThat(PayoutPolicy.FEE_RATE_PERCENT).isEqualTo(3);
        assertThat(PayoutPolicy.PAYOUT_DAY_OF_MONTH).isEqualTo(15);
    }

    @Test
    @DisplayName("수수료는 판매 금액의 3%이고 원 단위 미만은 내린다")
    void feeOf() {
        assertThat(PayoutPolicy.feeOf(10_000L)).isEqualTo(300L);
        assertThat(PayoutPolicy.feeOf(1_999L)).isEqualTo(59L);
        assertThat(PayoutPolicy.feeOf(33L)).isZero();
        assertThat(PayoutPolicy.feeOf(0L)).isZero();
    }

    @Test
    @DisplayName("지급 예정 시각은 구매확정한 달의 다음 달 15일 00:00이다")
    void scheduledAtFor() {
        assertThat(PayoutPolicy.scheduledAtFor(LocalDateTime.of(2026, 10, 1, 0, 0)))
                .isEqualTo(LocalDateTime.of(2026, 11, 15, 0, 0));
        assertThat(PayoutPolicy.scheduledAtFor(LocalDateTime.of(2026, 10, 31, 23, 59, 59)))
                .isEqualTo(LocalDateTime.of(2026, 11, 15, 0, 0));
        assertThat(PayoutPolicy.scheduledAtFor(LocalDateTime.of(2026, 12, 31, 12, 0)))
                .isEqualTo(LocalDateTime.of(2027, 1, 15, 0, 0));
    }

    @Test
    @DisplayName("다음 지급일은 기준일이 지급일 이전이거나 당일이면 이번 달 15일이다")
    void nextPayoutDateThisMonth() {
        assertThat(PayoutPolicy.nextPayoutDate(LocalDate.of(2026, 10, 1))).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(PayoutPolicy.nextPayoutDate(LocalDate.of(2026, 10, 15))).isEqualTo(LocalDate.of(2026, 10, 15));
    }

    @Test
    @DisplayName("다음 지급일은 기준일이 지급일을 지났으면 다음 달 15일이다")
    void nextPayoutDateNextMonth() {
        assertThat(PayoutPolicy.nextPayoutDate(LocalDate.of(2026, 10, 16))).isEqualTo(LocalDate.of(2026, 11, 15));
        assertThat(PayoutPolicy.nextPayoutDate(LocalDate.of(2026, 12, 31))).isEqualTo(LocalDate.of(2027, 1, 15));
    }
}
