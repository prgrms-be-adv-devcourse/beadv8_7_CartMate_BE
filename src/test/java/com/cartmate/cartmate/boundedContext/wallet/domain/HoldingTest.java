package com.cartmate.cartmate.boundedContext.wallet.domain;

import com.cartmate.cartmate.shared.wallet.WalletPurchaseType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HoldingTest {

    private Holding holding;

    @BeforeEach
    void setUp() {
        holding = Holding.create(Wallet.createWallet(10L), 100L, WalletPurchaseType.INDIVIDUAL_GROUP, 10_000L, 200L);
    }

    @Test
    @DisplayName("남은 금액은 환불과 정산으로 꺼낸 금액을 뺀 값이다")
    void remainingAmount() {
        holding.addRefund(1_000L, 0L);

        assertThat(holding.remainingAmount(3_000L)).isEqualTo(6_000L);
    }

    @Test
    @DisplayName("정산으로 남은 금액을 모두 꺼내면 정산 완료 상태가 된다")
    void closeAfterSettlement() {
        holding.closeIfExhausted(4_000L);
        assertThat(holding.getStatus()).isEqualTo(HoldingStatus.RESERVED);

        holding.closeIfExhausted(10_000L);

        assertThat(holding.getStatus()).isEqualTo(HoldingStatus.SETTLED);
        assertThat(holding.getSettledAt()).isNotNull();
    }

    @Test
    @DisplayName("정산 없이 전액 환불되면 취소 상태가 된다")
    void fullRefundWithoutSettlement() {
        holding.addRefund(10_000L, 0L);

        assertThat(holding.getStatus()).isEqualTo(HoldingStatus.CANCELED);
    }

    @Test
    @DisplayName("정산된 금액이 있는 홀딩의 남은 금액을 모두 환불하면 정산 완료 상태가 된다")
    void refundRemainingAfterSettlement() {
        holding.addRefund(6_000L, 4_000L);

        assertThat(holding.getStatus()).isEqualTo(HoldingStatus.SETTLED);
    }
}
