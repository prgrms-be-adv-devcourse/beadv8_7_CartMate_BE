package com.cartmate.cartmate.boundedContext.memberService.member.out.account;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenBankingPropertiesTest {

    @Test
    @DisplayName("모드가 비어 있으면 fake로 본다")
    void defaultsToFake() {
        assertThat(new OpenBankingProperties(null, "", "", "").mode()).isEqualTo("fake");
        assertThat(new OpenBankingProperties(" ", "", "", "").mode()).isEqualTo("fake");
    }

    @Test
    @DisplayName("fake 모드는 키가 없어도 만들어진다")
    void fakeNeedsNoKeys() {
        assertThat(new OpenBankingProperties("fake", "", "", "").mode()).isEqualTo("fake");
    }

    @Test
    @DisplayName("live 모드에서 키가 하나라도 비어 있으면 실패한다")
    void liveRequiresKeys() {
        assertThatThrownBy(() -> new OpenBankingProperties("live", "id", "secret", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OPENBANKING_ORG_CODE");
    }

    @Test
    @DisplayName("live 모드에서 키가 모두 있으면 만들어진다")
    void liveWithKeys() {
        assertThat(new OpenBankingProperties("live", "id", "secret", "org").mode()).isEqualTo("live");
    }
}
