package com.cartmate.cartmate.boundedContext.payout.domain;

import com.cartmate.cartmate.boundedContext.payout.exception.PayoutErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayoutTest {

    private static final LocalDateTime CONFIRMED_AT = LocalDateTime.of(2026, 10, 19, 13, 30);

    private Payout payout(long salesAmount, LocalDateTime confirmedAt) {
        return Payout.create(1L, 100L, PayoutPurchaseType.INDIVIDUAL_GROUP, 1000L, "티셔츠", 1L, salesAmount, confirmedAt.minusDays(3), confirmedAt);
    }

    @Test
    @DisplayName("수수료 3%를 제외한 금액이 정산 금액이 되고 PENDING으로 생성된다")
    void createCalculatesFee() {
        Payout payout = payout(10_000L, CONFIRMED_AT);

        assertThat(payout.getSalesAmount()).isEqualTo(10_000L);
        assertThat(payout.getFeeAmount()).isEqualTo(300L);
        assertThat(payout.getPayoutAmount()).isEqualTo(9_700L);
        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.PENDING);
        assertThat(payout.getConfirmedAt()).isEqualTo(CONFIRMED_AT);
    }

    @Test
    @DisplayName("수수료는 원 단위 미만을 버리고 판매자가 나머지를 받는다")
    void feeIsFlooredToWon() {
        Payout payout = payout(1_999L, CONFIRMED_AT);

        assertThat(payout.getFeeAmount()).isEqualTo(59L);
        assertThat(payout.getPayoutAmount()).isEqualTo(1_940L);
    }

    @Test
    @DisplayName("0원 상품도 정산 기록이 생성되고 수수료와 정산 금액은 0원이다")
    void zeroAmountPayoutIsRecorded() {
        Payout payout = payout(0L, CONFIRMED_AT);

        assertThat(payout.getSalesAmount()).isZero();
        assertThat(payout.getFeeAmount()).isZero();
        assertThat(payout.getPayoutAmount()).isZero();
        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.PENDING);
    }

    @Test
    @DisplayName("구매확정 월의 다음 달 15일 00:00에 지급 예정이다")
    void scheduledAtIsNextMonth15th() {
        assertThat(payout(1_000L, CONFIRMED_AT).getScheduledAt())
                .isEqualTo(LocalDateTime.of(2026, 11, 15, 0, 0));
        assertThat(payout(1_000L, LocalDateTime.of(2026, 10, 1, 0, 0)).getScheduledAt())
                .isEqualTo(LocalDateTime.of(2026, 11, 15, 0, 0));
        assertThat(payout(1_000L, LocalDateTime.of(2026, 10, 31, 23, 59, 59)).getScheduledAt())
                .isEqualTo(LocalDateTime.of(2026, 11, 15, 0, 0));
    }

    @Test
    @DisplayName("12월 구매확정은 다음 해 1월 15일에 지급 예정이다")
    void scheduledAtCrossesYear() {
        assertThat(payout(1_000L, LocalDateTime.of(2026, 12, 31, 12, 0)).getScheduledAt())
                .isEqualTo(LocalDateTime.of(2027, 1, 15, 0, 0));
    }

    @Test
    @DisplayName("대기 정산을 완료하면 COMPLETED가 되고 지급 시각이 기록된다")
    void complete() {
        Payout payout = payout(10_000L, CONFIRMED_AT);
        LocalDateTime paidAt = LocalDateTime.of(2026, 11, 15, 0, 10);

        payout.complete(paidAt);

        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.COMPLETED);
        assertThat(payout.getPaidAt()).isEqualTo(paidAt);
    }

    @Test
    @DisplayName("이미 완료된 정산은 다시 완료할 수 없다")
    void completeTwice() {
        Payout payout = payout(10_000L, CONFIRMED_AT);
        payout.complete(LocalDateTime.of(2026, 11, 15, 0, 10));

        assertThatThrownBy(() -> payout.complete(LocalDateTime.of(2026, 11, 16, 0, 10)))
                .isInstanceOfSatisfying(ServiceException.class, e -> assertThat(e.getResultCode())
                        .isEqualTo(PayoutErrorCode.PAYOUT_NOT_PENDING.resultCode()));
    }

    @Test
    @DisplayName("필수 값이 없거나 금액이 음수이면 생성할 수 없다")
    void createValidation() {
        assertThatThrownBy(() -> Payout.create(null, 100L, PayoutPurchaseType.INDIVIDUAL_GROUP, 1000L, "티셔츠", 1L, 1_000L,
                CONFIRMED_AT, CONFIRMED_AT))
                .isInstanceOfSatisfying(ServiceException.class, e -> assertThat(e.getResultCode())
                        .isEqualTo(PayoutErrorCode.PAYOUT_REQUIRED_VALUE_MISSING.resultCode()));
        assertThatThrownBy(() -> Payout.create(1L, 100L, PayoutPurchaseType.INDIVIDUAL_GROUP, 1000L, "티셔츠", 1L, 1_000L,
                CONFIRMED_AT, null))
                .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> Payout.create(1L, 100L, null, 1000L, "티셔츠", 1L, 1_000L,
                CONFIRMED_AT, CONFIRMED_AT))
                .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> payout(-1L, CONFIRMED_AT))
                .isInstanceOfSatisfying(ServiceException.class, e -> assertThat(e.getResultCode())
                        .isEqualTo(PayoutErrorCode.PAYOUT_INVALID_AMOUNT.resultCode()));
    }
}
