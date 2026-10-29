package com.cartmate.cartmate.boundedContext.trust.domain;

import com.cartmate.cartmate.boundedContext.trust.exception.TrustErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrustMetricsTest {

    private void assertError(Runnable action, TrustErrorCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getResultCode()).isEqualTo(code.resultCode()));
    }

    @Test
    @DisplayName("구매확정과 귀책 클레임 건수로 집계값을 만든다")
    void create() {
        TrustMetrics metrics = new TrustMetrics(10, 2);

        assertThat(metrics.confirmedCount()).isEqualTo(10);
        assertThat(metrics.faultClaimCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("0건인 집계값도 만들 수 있다")
    void createWithZero() {
        TrustMetrics metrics = new TrustMetrics(0, 0);

        assertThat(metrics.confirmedCount()).isZero();
        assertThat(metrics.faultClaimCount()).isZero();
    }

    @Test
    @DisplayName("건수가 음수이면 집계값을 만들 수 없다")
    void createValidation() {
        assertError(() -> new TrustMetrics(-1, 0), TrustErrorCode.TRUST_INVALID_METRICS);
        assertError(() -> new TrustMetrics(0, -1), TrustErrorCode.TRUST_INVALID_METRICS);
    }
}
