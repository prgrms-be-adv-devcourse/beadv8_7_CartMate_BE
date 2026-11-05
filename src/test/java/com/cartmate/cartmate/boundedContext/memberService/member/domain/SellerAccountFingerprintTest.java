package com.cartmate.cartmate.boundedContext.memberService.member.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SellerAccountFingerprintTest {

    private String business(String bank, String number, String holder, String name, String rep, String bizNo) {
        return SellerAccountFingerprint.of(SellerType.BUSINESS, bank, number, holder, name, rep, bizNo);
    }

    @Test
    @DisplayName("같은 입력은 같은 해시가 된다")
    void sameInputSameHash() {
        assertThat(business("신한은행", "110123456789", "홍길동", "팀바이", "홍길동", "123-45-67890"))
                .isEqualTo(business("신한은행", "110123456789", "홍길동", "팀바이", "홍길동", "123-45-67890"));
    }

    @Test
    @DisplayName("입력이 하나만 달라도 해시가 달라진다")
    void anyChangeChangesHash() {
        String base = business("신한은행", "110123456789", "홍길동", "팀바이", "홍길동", "123-45-67890");

        assertThat(business("국민은행", "110123456789", "홍길동", "팀바이", "홍길동", "123-45-67890")).isNotEqualTo(base);
        assertThat(business("신한은행", "110123456780", "홍길동", "팀바이", "홍길동", "123-45-67890")).isNotEqualTo(base);
        assertThat(business("신한은행", "110123456789", "김길동", "팀바이", "홍길동", "123-45-67890")).isNotEqualTo(base);
        assertThat(business("신한은행", "110123456789", "홍길동", "팀바이2", "홍길동", "123-45-67890")).isNotEqualTo(base);
        assertThat(business("신한은행", "110123456789", "홍길동", "팀바이", "김대표", "123-45-67890")).isNotEqualTo(base);
        assertThat(business("신한은행", "110123456789", "홍길동", "팀바이", "홍길동", "123-45-67891")).isNotEqualTo(base);
    }

    @Test
    @DisplayName("필드 경계가 달라도 같은 문자열로 합쳐지지 않는다")
    void fieldBoundariesAreNotAmbiguous() {
        assertThat(SellerAccountFingerprint.of(SellerType.INDIVIDUAL, "신한은행", "110123456789", "AB", null, null, null))
                .isNotEqualTo(SellerAccountFingerprint.of(SellerType.INDIVIDUAL, "신한은행", "1101234567891", "B", null, null, null));
    }

    @Test
    @DisplayName("개인은 사업자 정보가 달라도 해시가 같다")
    void individualIgnoresBusinessFields() {
        assertThat(SellerAccountFingerprint.of(SellerType.INDIVIDUAL, "신한은행", "110123456789", "홍길동", "팀바이", "홍길동", "123-45-67890"))
                .isEqualTo(SellerAccountFingerprint.of(SellerType.INDIVIDUAL, "신한은행", "110123456789", "홍길동", null, null, null));
    }

    @Test
    @DisplayName("개인과 사업자는 같은 계좌여도 해시가 다르다")
    void typeIsPartOfHash() {
        assertThat(SellerAccountFingerprint.of(SellerType.INDIVIDUAL, "신한은행", "110123456789", "홍길동", null, null, null))
                .isNotEqualTo(SellerAccountFingerprint.of(SellerType.BUSINESS, "신한은행", "110123456789", "홍길동", null, null, null));
    }
}
