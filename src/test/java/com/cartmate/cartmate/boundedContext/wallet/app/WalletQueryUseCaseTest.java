package com.cartmate.cartmate.boundedContext.wallet.app;

import com.cartmate.cartmate.boundedContext.wallet.app.dto.WalletBalanceResponse;
import com.cartmate.cartmate.boundedContext.wallet.domain.HoldingStatus;
import com.cartmate.cartmate.boundedContext.wallet.domain.Wallet;
import com.cartmate.cartmate.boundedContext.wallet.out.HoldingRepository;
import com.cartmate.cartmate.boundedContext.wallet.out.HoldingSettlementRepository;
import com.cartmate.cartmate.boundedContext.wallet.out.WalletRepository;
import com.cartmate.cartmate.boundedContext.wallet.out.WalletTransactionRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WalletQueryUseCaseTest {

    private static final long MEMBER_ID = 10L;
    private static final long WALLET_ID = 100L;

    private final WalletRepository walletRepository = mock(WalletRepository.class);
    private final WalletTransactionRepository transactionRepository = mock(WalletTransactionRepository.class);
    private final HoldingRepository holdingRepository = mock(HoldingRepository.class);
    private final HoldingSettlementRepository holdingSettlementRepository = mock(HoldingSettlementRepository.class);
    private WalletQueryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new WalletQueryUseCase(walletRepository, transactionRepository, holdingRepository, holdingSettlementRepository);

        Wallet wallet = Wallet.createWallet(MEMBER_ID);
        ReflectionTestUtils.setField(wallet, "id", WALLET_ID);
        wallet.charge(50_000L);
        when(walletRepository.findByMemberId(MEMBER_ID)).thenReturn(Optional.of(wallet));
    }

    @Test
    @DisplayName("동결 금액은 홀딩 중인 홀딩의 금액에서 환불 누적액과 정산으로 꺼낸 금액을 뺀 값이다")
    void heldBalanceIsRemainingOfReservedHoldings() {
        // 홀딩 금액 합계에서 환불로 돌려준 금액을 뺀 값이 40,000원, 그중 정산으로 꺼낸 금액이 15,000원이다.
        when(holdingRepository.sumUnrefundedAmountByWalletIdAndStatus(WALLET_ID, HoldingStatus.RESERVED)).thenReturn(40_000L);
        when(holdingSettlementRepository.sumAmountByWalletIdAndHoldingStatus(WALLET_ID, HoldingStatus.RESERVED)).thenReturn(15_000L);

        WalletBalanceResponse response = useCase.getWallet(MEMBER_ID);

        assertThat(response.walletId()).isEqualTo(WALLET_ID);
        assertThat(response.balance()).isEqualTo(50_000L);
        assertThat(response.heldBalance()).isEqualTo(25_000L);
    }

    @Test
    @DisplayName("홀딩이 없으면 동결 금액은 0이다")
    void heldBalanceIsZeroWithoutHoldings() {
        when(holdingRepository.sumUnrefundedAmountByWalletIdAndStatus(WALLET_ID, HoldingStatus.RESERVED)).thenReturn(0L);
        when(holdingSettlementRepository.sumAmountByWalletIdAndHoldingStatus(WALLET_ID, HoldingStatus.RESERVED)).thenReturn(0L);

        assertThat(useCase.getWallet(MEMBER_ID).heldBalance()).isZero();
    }

    @Test
    @DisplayName("지갑이 없으면 지갑을 찾을 수 없다는 오류다")
    void walletNotFound() {
        when(walletRepository.findByMemberId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.getWallet(99L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("WALLET_NOT_FOUND");
    }
}
