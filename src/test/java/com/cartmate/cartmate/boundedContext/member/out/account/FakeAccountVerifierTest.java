package com.cartmate.cartmate.boundedContext.member.out.account;

import com.cartmate.cartmate.boundedContext.member.domain.BankCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FakeAccountVerifierTest {

    private final FakeAccountVerifier verifier = new FakeAccountVerifier();

    @Test
    @DisplayName("일반 계좌번호는 통과한다")
    void matched() {
        assertThat(verifier.isHolderMatched(BankCode.SHINHAN, "110123456789", "900101", List.of("홍길동"))).isTrue();
    }

    @Test
    @DisplayName("0000으로 끝나는 계좌번호는 불일치로 응답한다")
    void mismatched() {
        assertThat(verifier.isHolderMatched(BankCode.SHINHAN, "110123450000", "900101", List.of("홍길동"))).isFalse();
    }

    @Test
    @DisplayName("9999로 끝나는 계좌번호는 호출 실패(503)로 응답한다")
    void unavailable() {
        assertThatThrownBy(() -> verifier.isHolderMatched(BankCode.SHINHAN, "110123459999", "900101", List.of("홍길동")))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getResultCode())
                .isEqualTo("503-SELLER_ACCOUNT_VERIFY_UNAVAILABLE");
    }
}
