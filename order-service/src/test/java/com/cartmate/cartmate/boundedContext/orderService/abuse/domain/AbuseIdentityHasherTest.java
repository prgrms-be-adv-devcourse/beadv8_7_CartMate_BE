package com.cartmate.cartmate.boundedContext.orderService.abuse.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AbuseIdentityHasherTest {

    @Test
    @DisplayName("전화번호는 숫자만 남겨 비교하므로 표기가 달라도 같은 해시가 되고, 원문은 남지 않는다")
    void phoneIgnoresFormatting() {
        String hash = AbuseIdentityHasher.phone("010-1234-5678").orElseThrow();

        assertThat(AbuseIdentityHasher.phone("010 1234 5678")).contains(hash);
        assertThat(AbuseIdentityHasher.phone("01012345678")).contains(hash);
        assertThat(AbuseIdentityHasher.phone("010-1234-5679")).isPresent().get().isNotEqualTo(hash);
        assertThat(hash).hasSize(64).doesNotContain("01012345678");
    }

    @Test
    @DisplayName("너무 짧거나 비어 있는 전화번호는 식별에 쓰지 않는다")
    void skipsUnusablePhone() {
        assertThat(AbuseIdentityHasher.phone(null)).isEmpty();
        assertThat(AbuseIdentityHasher.phone("1234")).isEmpty();
        assertThat(AbuseIdentityHasher.phone("--")).isEmpty();
    }

    @Test
    @DisplayName("주소는 공백 · 문장부호 · 대소문자를 무시하고, 주소나 상세주소가 비어 있으면 건물 전체가 묶이지 않게 식별에 쓰지 않는다")
    void addressIgnoresSpacingAndPunctuation() {
        String hash = AbuseIdentityHasher.address("06236", "서울 강남구 테헤란로 123", "101동 202호").orElseThrow();

        assertThat(AbuseIdentityHasher.address("06236", "서울강남구 테헤란로123", "101동, 202호")).contains(hash);
        assertThat(AbuseIdentityHasher.address("06236", "서울 강남구 테헤란로 123", "101동 203호"))
                .isPresent().get().isNotEqualTo(hash);
        assertThat(AbuseIdentityHasher.address("06236", " ", "101동")).isEmpty();
        assertThat(AbuseIdentityHasher.address("06236", "서울 강남구 테헤란로 123", " ")).isEmpty();
    }

    @Test
    @DisplayName("같은 값이라도 전화와 주소는 서로 다른 해시가 된다")
    void typesDoNotCollide() {
        assertThat(AbuseIdentityHasher.phone("0212345678"))
                .isNotEqualTo(AbuseIdentityHasher.address("", "0212345678", ""));
    }
}
