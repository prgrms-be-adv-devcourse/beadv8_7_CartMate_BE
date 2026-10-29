package com.cartmate.cartmate.boundedContext.trust.domain;

import com.cartmate.cartmate.boundedContext.trust.exception.TrustErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SellerTrustScoreTest {

    private static final LocalDateTime CALCULATED_AT = LocalDateTime.of(2026, 10, 29, 3, 0);

    private final TrustMetrics metrics = new TrustMetrics(100, 5);
    private final TrustEvaluation evaluation = new TrustEvaluation(90, TrustLevel.HIGH);

    private void assertError(Runnable action, TrustErrorCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getResultCode()).isEqualTo(code.resultCode()));
    }

    @Test
    @DisplayName("판매자 ID, 집계값, 평가 결과로 신뢰도 점수를 만든다")
    void create() {
        SellerTrustScore trustScore = SellerTrustScore.create(1L, metrics, evaluation, CALCULATED_AT);

        assertThat(trustScore.getSellerId()).isEqualTo(1L);
        assertThat(trustScore.getScore()).isEqualTo(90);
        assertThat(trustScore.getLevel()).isEqualTo(TrustLevel.HIGH);
        assertThat(trustScore.getConfirmedCount()).isEqualTo(100);
        assertThat(trustScore.getFaultClaimCount()).isEqualTo(5);
        assertThat(trustScore.getCalculatedAt()).isEqualTo(CALCULATED_AT);
    }

    @Test
    @DisplayName("NEW 등급이면 점수 없이 만들어진다")
    void createNew() {
        SellerTrustScore trustScore = SellerTrustScore.create(
                1L, new TrustMetrics(2, 0), new TrustEvaluation(null, TrustLevel.NEW), CALCULATED_AT);

        assertThat(trustScore.getScore()).isNull();
        assertThat(trustScore.getLevel()).isEqualTo(TrustLevel.NEW);
    }

    @Test
    @DisplayName("판매자 ID가 없으면 만들 수 없다")
    void createWithoutSellerId() {
        assertError(() -> SellerTrustScore.create(null, metrics, evaluation, CALCULATED_AT),
                TrustErrorCode.TRUST_SELLER_ID_REQUIRED);
    }

    @Test
    @DisplayName("집계값, 평가 결과, 계산 시각이 없으면 만들 수 없다")
    void createValidation() {
        assertError(() -> SellerTrustScore.create(1L, null, evaluation, CALCULATED_AT),
                TrustErrorCode.TRUST_INVALID_METRICS);
        assertError(() -> SellerTrustScore.create(1L, metrics, null, CALCULATED_AT),
                TrustErrorCode.TRUST_INVALID_METRICS);
        assertError(() -> SellerTrustScore.create(1L, metrics, evaluation, null),
                TrustErrorCode.TRUST_INVALID_METRICS);
    }

    @Test
    @DisplayName("다시 계산하면 점수, 등급, 집계값, 계산 시각이 새 값으로 바뀐다")
    void recalculate() {
        SellerTrustScore trustScore = SellerTrustScore.create(1L, metrics, evaluation, CALCULATED_AT);
        LocalDateTime recalculatedAt = CALCULATED_AT.plusDays(1);

        trustScore.recalculate(new TrustMetrics(120, 30), new TrustEvaluation(50, TrustLevel.LOW), recalculatedAt);

        assertThat(trustScore.getSellerId()).isEqualTo(1L);
        assertThat(trustScore.getScore()).isEqualTo(50);
        assertThat(trustScore.getLevel()).isEqualTo(TrustLevel.LOW);
        assertThat(trustScore.getConfirmedCount()).isEqualTo(120);
        assertThat(trustScore.getFaultClaimCount()).isEqualTo(30);
        assertThat(trustScore.getCalculatedAt()).isEqualTo(recalculatedAt);
    }

    @Test
    @DisplayName("다시 계산할 때 집계값, 평가 결과, 계산 시각이 없으면 기존 값을 바꾸지 않고 예외가 난다")
    void recalculateValidation() {
        SellerTrustScore trustScore = SellerTrustScore.create(1L, metrics, evaluation, CALCULATED_AT);

        assertError(() -> trustScore.recalculate(null, evaluation, CALCULATED_AT), TrustErrorCode.TRUST_INVALID_METRICS);
        assertError(() -> trustScore.recalculate(metrics, null, CALCULATED_AT), TrustErrorCode.TRUST_INVALID_METRICS);
        assertError(() -> trustScore.recalculate(metrics, evaluation, null), TrustErrorCode.TRUST_INVALID_METRICS);
        assertThat(trustScore.getScore()).isEqualTo(90);
    }

    @Test
    @DisplayName("점수와 등급이 모두 같으면 변경되지 않은 것이다")
    void isChangedWhenSame() {
        SellerTrustScore trustScore = SellerTrustScore.create(1L, metrics, evaluation, CALCULATED_AT);

        assertThat(trustScore.isChanged(new TrustEvaluation(90, TrustLevel.HIGH))).isFalse();
    }

    @Test
    @DisplayName("점수나 등급이 다르면 변경된 것이다")
    void isChangedWhenDifferent() {
        SellerTrustScore trustScore = SellerTrustScore.create(1L, metrics, evaluation, CALCULATED_AT);

        assertThat(trustScore.isChanged(new TrustEvaluation(89, TrustLevel.HIGH))).isTrue();
        assertThat(trustScore.isChanged(new TrustEvaluation(90, TrustLevel.NORMAL))).isTrue();
    }

    @Test
    @DisplayName("점수가 없는 NEW 등급끼리는 변경되지 않은 것이고, 점수가 생기면 변경된 것이다")
    void isChangedWithNullScore() {
        SellerTrustScore trustScore = SellerTrustScore.create(
                1L, new TrustMetrics(2, 0), new TrustEvaluation(null, TrustLevel.NEW), CALCULATED_AT);

        assertThat(trustScore.isChanged(new TrustEvaluation(null, TrustLevel.NEW))).isFalse();
        assertThat(trustScore.isChanged(new TrustEvaluation(100, TrustLevel.HIGH))).isTrue();
    }
}
