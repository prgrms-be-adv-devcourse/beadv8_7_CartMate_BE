package com.cartmate.cartmate.boundedContext.orderService.order.domain.personal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaybackPayoutTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 6, 13, 0);

    @Test
    @DisplayName("다시 보내면 될 수 있는 실패는 자동 재요청 횟수 안에서 대기로, 다 쓰면 실패로 확정한다")
    void retryableFailureUntilLimit() {
        PaybackPayout payout = PaybackPayout.request(7L, 10L, 1_000L, NOW);

        for (int attempt = 1; attempt < PaybackPayout.MAX_AUTO_ATTEMPTS; attempt++) {
            assertThat(payout.markFailed("WALLET_BUSY", true, payout.getAttempt(), NOW)).isTrue();
            assertThat(payout.getStatus()).isEqualTo(PaybackPayoutStatus.RETRY_WAITING);
            payout.retry(NOW.plusMinutes(attempt));
            assertThat(payout.getStatus()).isEqualTo(PaybackPayoutStatus.REQUESTED);
        }
        payout.markFailed("WALLET_BUSY", true, payout.getAttempt(), NOW.plusHours(1));

        assertThat(payout.getStatus()).isEqualTo(PaybackPayoutStatus.FAILED);
        assertThat(payout.getAttempt()).isEqualTo(PaybackPayout.MAX_AUTO_ATTEMPTS);
        assertThat(payout.getCompletedAt()).isEqualTo(NOW.plusHours(1));
    }

    @Test
    @DisplayName("다시 보내도 같은 실패는 바로 실패로 확정하고, 어드민 재요청으로 다시 요청 상태가 된다")
    void permanentFailureThenAdminRetry() {
        PaybackPayout payout = PaybackPayout.request(7L, 10L, 1_000L, NOW);

        payout.markFailed("WALLET_NOT_FOUND", false, payout.getAttempt(), NOW);
        assertThat(payout.getStatus()).isEqualTo(PaybackPayoutStatus.FAILED);
        assertThat(payout.getFailureCode()).isEqualTo("WALLET_NOT_FOUND");

        payout.retry(NOW.plusDays(1));
        assertThat(payout.getStatus()).isEqualTo(PaybackPayoutStatus.REQUESTED);
        assertThat(payout.getAttempt()).isEqualTo(2);
        assertThat(payout.getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("자동 재요청을 다 쓴 뒤 어드민이 다시 요청한 건은 재시도 가능한 실패여도 바로 실패로 확정한다")
    void adminRetryAfterLimit() {
        PaybackPayout payout = PaybackPayout.request(7L, 10L, 1_000L, NOW);
        while (payout.markFailed("WALLET_BUSY", true, payout.getAttempt(), NOW) && payout.isRetryWaiting()) {
            payout.retry(NOW);
        }
        payout.retry(NOW.plusDays(1));

        payout.markFailed("WALLET_BUSY", true, payout.getAttempt(), NOW.plusDays(1));

        assertThat(payout.getStatus()).isEqualTo(PaybackPayoutStatus.FAILED);
        assertThat(payout.getAttempt()).isEqualTo(PaybackPayout.MAX_AUTO_ATTEMPTS + 1);
    }

    @Test
    @DisplayName("지급 완료는 한 번만 반영하고, 실패 확정 뒤 늦게 온 성공도 반영한다")
    void paidOnce() {
        PaybackPayout payout = PaybackPayout.request(7L, 10L, 1_000L, NOW);
        payout.markFailed("WALLET_NOT_FOUND", false, payout.getAttempt(), NOW);

        assertThat(payout.markPaid(NOW.plusMinutes(1))).isTrue();
        assertThat(payout.markPaid(NOW.plusMinutes(2))).isFalse();
        assertThat(payout.markFailed("LATE", false, payout.getAttempt(), NOW.plusMinutes(3))).isFalse();

        assertThat(payout.getStatus()).isEqualTo(PaybackPayoutStatus.PAID);
        assertThat(payout.getCompletedAt()).isEqualTo(NOW.plusMinutes(1));
        assertThat(payout.getFailureCode()).isNull();
    }

    @Test
    @DisplayName("요청 중이거나 지급된 건은 재요청할 수 없고, 금액은 양수여야 한다")
    void invalid() {
        PaybackPayout payout = PaybackPayout.request(7L, 10L, 1_000L, NOW);

        assertThatThrownBy(() -> payout.retry(NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> PaybackPayout.request(7L, 10L, 0L, NOW)).isInstanceOf(IllegalArgumentException.class);
    }
}
