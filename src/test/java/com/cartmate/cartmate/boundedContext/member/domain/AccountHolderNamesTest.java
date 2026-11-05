package com.cartmate.cartmate.boundedContext.member.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class AccountHolderNamesTest {

    @ParameterizedTest(name = "[{index}] {0} = {1}")
    @DisplayName("표기만 다른 같은 이름은 일치한다")
    @CsvSource({
            "홍 길동, 홍길동",
            "KIM, kim",
            "(주)팀바이 스포츠, 주식회사 팀바이 스포츠",
            "(주)팀바이 스포츠, ㈜팀바이스포츠",
            "(주)팀바이, （주）팀바이",
            "( 주 )팀바이, (주)팀바이",
            "(유)팀바이, 유한회사 팀바이",
            "(유책)팀바이, 유한책임회사 팀바이"
    })
    void sameNames(String a, String b) {
        assertThat(AccountHolderNames.matches(a, b)).isTrue();
    }

    @ParameterizedTest(name = "[{index}] {0} ≠ {1}")
    @DisplayName("다른 법인이거나 부분 일치이면 일치하지 않는다")
    @CsvSource({
            "(주)팀바이, (유)팀바이",
            "(주)팀바이, 팀바이",
            "(주)팀바이, 팀바이(주)",
            "팀바이, (주)팀바이스포츠",
            "홍길동, 홍길동상회",
            "김, 김철수"
    })
    void differentNames(String a, String b) {
        assertThat(AccountHolderNames.matches(a, b)).isFalse();
    }

    @Test
    @DisplayName("빈 값과 null은 일치하지 않는다")
    void blankNames() {
        assertThat(AccountHolderNames.matches("", "")).isFalse();
        assertThat(AccountHolderNames.matches(null, null)).isFalse();
        assertThat(AccountHolderNames.matches("  ", "홍길동")).isFalse();
    }

    @Test
    @DisplayName("정규화는 법인 형태를 지우지 않고 표기만 통일한다")
    void normalizeKeepsCorporateForm() {
        assertThat(AccountHolderNames.normalize("주식회사 팀바이")).isEqualTo("(주)팀바이");
        assertThat(AccountHolderNames.normalize("㈜팀바이")).isEqualTo("(주)팀바이");
    }
}
