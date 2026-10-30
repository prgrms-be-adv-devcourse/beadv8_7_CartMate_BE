package com.cartmate.cartmate.boundedContext.post.domain;

import com.cartmate.cartmate.global.code.SalesPostErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SalesPostSellerTrustTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 30, 3, 0);

    private void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getResultCode()).isEqualTo(SalesPostErrorCode.SELLER_TRUST_INVALID.resultCode()));
    }

    @Test
    @DisplayName("판매자 ID, 점수, 등급, 계산 시각으로 신뢰도 복제본을 만든다")
    void create() {
        SalesPostSellerTrust trust = SalesPostSellerTrust.create(1L, 90, SellerTrustLevel.HIGH, AT);

        assertThat(trust.getSellerId()).isEqualTo(1L);
        assertThat(trust.getScore()).isEqualTo(90);
        assertThat(trust.getLevel()).isEqualTo(SellerTrustLevel.HIGH);
        assertThat(trust.getCalculatedAt()).isEqualTo(AT);
    }

    @Test
    @DisplayName("점수가 없는 NEW 등급으로도 만들 수 있다")
    void createNew() {
        SalesPostSellerTrust trust = SalesPostSellerTrust.create(1L, null, SellerTrustLevel.NEW, AT);

        assertThat(trust.getScore()).isNull();
        assertThat(trust.getLevel()).isEqualTo(SellerTrustLevel.NEW);
    }

    @Test
    @DisplayName("판매자 ID, 등급, 계산 시각이 없으면 만들 수 없다")
    void createValidation() {
        assertInvalid(() -> SalesPostSellerTrust.create(null, 90, SellerTrustLevel.HIGH, AT));
        assertInvalid(() -> SalesPostSellerTrust.create(1L, 90, null, AT));
        assertInvalid(() -> SalesPostSellerTrust.create(1L, 90, SellerTrustLevel.HIGH, null));
    }

    @Test
    @DisplayName("더 최근에 계산된 결과로 갱신한다")
    void updateWithNewer() {
        SalesPostSellerTrust trust = SalesPostSellerTrust.create(1L, 90, SellerTrustLevel.HIGH, AT);

        boolean updated = trust.update(60, SellerTrustLevel.LOW, AT.plusDays(1));

        assertThat(updated).isTrue();
        assertThat(trust.getScore()).isEqualTo(60);
        assertThat(trust.getLevel()).isEqualTo(SellerTrustLevel.LOW);
        assertThat(trust.getCalculatedAt()).isEqualTo(AT.plusDays(1));
    }

    @Test
    @DisplayName("계산 시각이 같으면 갱신한다")
    void updateWithSameTime() {
        SalesPostSellerTrust trust = SalesPostSellerTrust.create(1L, 90, SellerTrustLevel.HIGH, AT);

        assertThat(trust.update(80, SellerTrustLevel.NORMAL, AT)).isTrue();
        assertThat(trust.getScore()).isEqualTo(80);
    }

    @Test
    @DisplayName("저장된 값보다 오래된 계산 결과는 무시하고 값을 유지한다")
    void ignoreStale() {
        SalesPostSellerTrust trust = SalesPostSellerTrust.create(1L, 90, SellerTrustLevel.HIGH, AT);

        boolean updated = trust.update(60, SellerTrustLevel.LOW, AT.minusDays(1));

        assertThat(updated).isFalse();
        assertThat(trust.getScore()).isEqualTo(90);
        assertThat(trust.getLevel()).isEqualTo(SellerTrustLevel.HIGH);
        assertThat(trust.getCalculatedAt()).isEqualTo(AT);
    }

    @Test
    @DisplayName("갱신할 때 등급이나 계산 시각이 없으면 예외가 난다")
    void updateValidation() {
        SalesPostSellerTrust trust = SalesPostSellerTrust.create(1L, 90, SellerTrustLevel.HIGH, AT);

        assertInvalid(() -> trust.update(60, null, AT));
        assertInvalid(() -> trust.update(60, SellerTrustLevel.LOW, null));
    }

    @ParameterizedTest(name = "{0} 문자열은 같은 이름의 등급으로 바뀐다")
    @ValueSource(strings = {"NEW", "LOW", "NORMAL", "HIGH"})
    @DisplayName("알려진 등급 문자열을 등급으로 바꾼다")
    void levelFrom(String level) {
        assertThat(SellerTrustLevel.from(level).name()).isEqualTo(level);
    }

    @Test
    @DisplayName("모르는 등급 문자열이나 null은 추측하지 않고 예외가 난다")
    void levelFromUnknown() {
        assertInvalid(() -> SellerTrustLevel.from("EXCELLENT"));
        assertInvalid(() -> SellerTrustLevel.from("high"));
        assertInvalid(() -> SellerTrustLevel.from(""));
        assertInvalid(() -> SellerTrustLevel.from(null));
    }
}
