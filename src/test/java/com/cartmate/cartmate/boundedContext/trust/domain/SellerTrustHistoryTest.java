package com.cartmate.cartmate.boundedContext.trust.domain;

import com.cartmate.cartmate.boundedContext.trust.exception.TrustErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SellerTrustHistoryTest {

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 29, 12, 0);

    private void assertError(Runnable action, TrustErrorCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getResultCode()).isEqualTo(code.resultCode()));
    }

    @Test
    @DisplayName("판매자 ID, 유형, 식별 키, 발생 시각으로 거래 이력을 만든다")
    void create() {
        SellerTrustHistory history = SellerTrustHistory.create(
                1L, TrustHistoryType.PURCHASE_CONFIRMED, "10", OCCURRED_AT);

        assertThat(history.getSellerId()).isEqualTo(1L);
        assertThat(history.getType()).isEqualTo(TrustHistoryType.PURCHASE_CONFIRMED);
        assertThat(history.getSourceKey()).isEqualTo("10");
        assertThat(history.getOccurredAt()).isEqualTo(OCCURRED_AT);
    }

    @Test
    @DisplayName("판매자 ID, 유형, 발생 시각이 없으면 만들 수 없다")
    void createValidation() {
        assertError(() -> SellerTrustHistory.create(null, TrustHistoryType.FAULT_CLAIM, "10", OCCURRED_AT),
                TrustErrorCode.TRUST_SELLER_ID_REQUIRED);
        assertError(() -> SellerTrustHistory.create(1L, null, "10", OCCURRED_AT),
                TrustErrorCode.TRUST_HISTORY_TYPE_REQUIRED);
        assertError(() -> SellerTrustHistory.create(1L, TrustHistoryType.FAULT_CLAIM, "10", null),
                TrustErrorCode.TRUST_OCCURRED_AT_REQUIRED);
    }

    @Test
    @DisplayName("식별 키가 없거나 공백이면 만들 수 없다")
    void createWithoutSourceKey() {
        assertError(() -> SellerTrustHistory.create(1L, TrustHistoryType.FAULT_CLAIM, null, OCCURRED_AT),
                TrustErrorCode.TRUST_SOURCE_KEY_REQUIRED);
        assertError(() -> SellerTrustHistory.create(1L, TrustHistoryType.FAULT_CLAIM, "", OCCURRED_AT),
                TrustErrorCode.TRUST_SOURCE_KEY_REQUIRED);
        assertError(() -> SellerTrustHistory.create(1L, TrustHistoryType.FAULT_CLAIM, "  ", OCCURRED_AT),
                TrustErrorCode.TRUST_SOURCE_KEY_REQUIRED);
    }
}
