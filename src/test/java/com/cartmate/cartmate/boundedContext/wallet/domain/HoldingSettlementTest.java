package com.cartmate.cartmate.boundedContext.wallet.domain;

import com.cartmate.cartmate.boundedContext.wallet.exception.WalletErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.wallet.WalletPurchaseType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HoldingSettlementTest {

    private Holding holding;

    @BeforeEach
    void setUp() {
        holding = Holding.create(Wallet.createWallet(10L), 100L, WalletPurchaseType.INDIVIDUAL_GROUP, 10_000L, 200L);
    }

    private void assertError(Runnable action, WalletErrorCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getResultCode()).isEqualTo(code.resultCode()));
    }

    @Test
    @DisplayName("홀딩, 정산 ID, 금액으로 정산 기록을 만든다")
    void create() {
        HoldingSettlement settlement = HoldingSettlement.create(holding, 300L, 3_000L);

        assertThat(settlement.getHolding()).isSameAs(holding);
        assertThat(settlement.getPayoutId()).isEqualTo(300L);
        assertThat(settlement.getAmount()).isEqualTo(3_000L);
    }

    @Test
    @DisplayName("홀딩이나 정산 ID가 없거나 금액이 0 이하이면 만들 수 없다")
    void createValidation() {
        assertError(() -> HoldingSettlement.create(null, 300L, 3_000L), WalletErrorCode.WALLET_HOLDING_NOT_FOUND);
        assertError(() -> HoldingSettlement.create(holding, null, 3_000L), WalletErrorCode.WALLET_PAYOUT_ID_REQUIRED);
        assertError(() -> HoldingSettlement.create(holding, 300L, null), WalletErrorCode.WALLET_INVALID_AMOUNT);
        assertError(() -> HoldingSettlement.create(holding, 300L, 0L), WalletErrorCode.WALLET_INVALID_AMOUNT);
    }
}
