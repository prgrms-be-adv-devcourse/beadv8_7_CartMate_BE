package com.cartmate.cartmate.boundedContext.member.domain;

import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BankCodeTest {

    @Test
    @DisplayName("은행 이름으로 표준 코드를 찾는다")
    void fromName() {
        assertThat(BankCode.fromName("신한은행")).isEqualTo(BankCode.SHINHAN);
        assertThat(BankCode.fromName("카카오뱅크").getCode()).isEqualTo("090");
    }

    @Test
    @DisplayName("프런트 은행 목록 8개가 모두 매핑된다")
    void allFrontendBanksAreMapped() {
        for (String name : new String[]{"신한은행", "국민은행", "우리은행", "하나은행", "농협은행", "기업은행", "카카오뱅크", "토스뱅크"}) {
            assertThat(BankCode.fromName(name).getDisplayName()).isEqualTo(name);
        }
    }

    @Test
    @DisplayName("목록에 없는 은행은 400으로 거절한다")
    void unsupportedBank() {
        assertThatThrownBy(() -> BankCode.fromName("없는은행"))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getResultCode())
                .isEqualTo("400-SELLER_BANK_NOT_SUPPORTED");
    }
}
