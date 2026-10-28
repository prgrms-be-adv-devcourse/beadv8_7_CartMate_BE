package com.cartmate.cartmate.boundedContext.payout.domain;

import com.cartmate.cartmate.boundedContext.payout.exception.PayoutErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettlementKeyTest {

    @Test
    @DisplayName("정산 ID는 지급 예정일과 상태로 만들고 다시 해석하면 같은 값이 된다")
    void idRoundTrip() {
        SettlementKey key = new SettlementKey(LocalDate.of(2026, 11, 15), PayoutStatus.COMPLETED);

        assertThat(key.id()).isEqualTo("20261115-COMPLETED");
        assertThat(SettlementKey.parse("20261115-COMPLETED")).isEqualTo(key);
        assertThat(SettlementKey.parse("20261115-PENDING").status()).isEqualTo(PayoutStatus.PENDING);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "20261115", "20261115-", "2026-11-15-PENDING", "20261315-PENDING",
            "20261115-UNKNOWN", "20261115-CANCELED", "20261115-FAILED", "20261115-pending"})
    @DisplayName("형식이 틀리거나 화면에 나오지 않는 상태이면 PAYOUT_INVALID_SETTLEMENT_ID 예외가 발생한다")
    void invalid(String settlementId) {
        assertThatThrownBy(() -> SettlementKey.parse(settlementId))
                .isInstanceOfSatisfying(ServiceException.class, e -> assertThat(e.getResultCode())
                        .isEqualTo(PayoutErrorCode.PAYOUT_INVALID_SETTLEMENT_ID.resultCode()));
    }
}
