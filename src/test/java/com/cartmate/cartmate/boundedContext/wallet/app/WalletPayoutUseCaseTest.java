package com.cartmate.cartmate.boundedContext.wallet.app;

import com.cartmate.cartmate.boundedContext.wallet.domain.ReferenceType;
import com.cartmate.cartmate.boundedContext.wallet.domain.Wallet;
import com.cartmate.cartmate.boundedContext.wallet.domain.WalletTransaction;
import com.cartmate.cartmate.boundedContext.wallet.domain.WalletTransactionType;
import com.cartmate.cartmate.boundedContext.wallet.exception.WalletErrorCode;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WalletPayoutUseCaseTest {

    private static final long WALLET_ID = 1L;
    private static final long PAYOUT_ID = 300L;
    private static final long ORDER_ID = 100L;

    private final WalletRepository walletRepository = mock(WalletRepository.class);
    private final WalletTransactionRepository walletTransactionRepository = mock(WalletTransactionRepository.class);
    private final LedgerRecorder ledgerRecorder = mock(LedgerRecorder.class);
    private WalletPayoutUseCase useCase;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        useCase = new WalletPayoutUseCase(walletRepository, walletTransactionRepository, ledgerRecorder);

        wallet = Wallet.createWallet(10L);
        ReflectionTestUtils.setField(wallet, "id", WALLET_ID);
        when(walletRepository.findById(WALLET_ID)).thenReturn(Optional.of(wallet));
        when(walletTransactionRepository.findByTypeAndOrderIdAndReferenceIdAndReferenceType(
                any(), any(), any(), any())).thenReturn(Optional.empty());
    }

    private void assertError(Runnable action, WalletErrorCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getResultCode()).isEqualTo(code.resultCode()));
    }

    @Test
    @DisplayName("정산금을 입금하면 잔액이 늘고 정산 거래 내역이 남는다")
    void deposit() {
        useCase.deposit(WALLET_ID, 9_700L, PAYOUT_ID, ORDER_ID);

        assertThat(wallet.getBalance()).isEqualTo(9_700L);
        verify(ledgerRecorder).record(wallet, WalletTransactionType.SETTLEMENT, ORDER_ID,
                PAYOUT_ID, ReferenceType.SETTLEMENT, null, 9_700L);
    }

    @Test
    @DisplayName("같은 정산 ID로 다시 입금을 요청하면 잔액과 거래 내역을 바꾸지 않는다")
    void duplicateDepositIsIdempotent() {
        WalletTransaction existing = WalletTransaction.create(wallet, WalletTransactionType.SETTLEMENT,
                ORDER_ID, PAYOUT_ID, ReferenceType.SETTLEMENT, null, 9_700L, 9_700L);
        when(walletTransactionRepository.findByTypeAndOrderIdAndReferenceIdAndReferenceType(
                WalletTransactionType.SETTLEMENT, ORDER_ID, PAYOUT_ID, ReferenceType.SETTLEMENT))
                .thenReturn(Optional.of(existing));

        useCase.deposit(WALLET_ID, 9_700L, PAYOUT_ID, ORDER_ID);

        assertThat(wallet.getBalance()).isZero();
        verify(ledgerRecorder, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("같은 정산 ID에 금액이 다르면 예외가 발생한다")
    void duplicateDepositWithDifferentAmount() {
        WalletTransaction existing = WalletTransaction.create(wallet, WalletTransactionType.SETTLEMENT,
                ORDER_ID, PAYOUT_ID, ReferenceType.SETTLEMENT, null, 9_700L, 9_700L);
        when(walletTransactionRepository.findByTypeAndOrderIdAndReferenceIdAndReferenceType(
                WalletTransactionType.SETTLEMENT, ORDER_ID, PAYOUT_ID, ReferenceType.SETTLEMENT))
                .thenReturn(Optional.of(existing));

        assertError(() -> useCase.deposit(WALLET_ID, 5_000L, PAYOUT_ID, ORDER_ID),
                WalletErrorCode.WALLET_TRANSACTION_AMOUNT_MISMATCH);
    }

    @Test
    @DisplayName("같은 정산 ID라도 다른 지갑의 거래 내역이면 접근 거부 예외가 발생한다")
    void duplicateDepositOfOtherWallet() {
        Wallet other = Wallet.createWallet(20L);
        ReflectionTestUtils.setField(other, "id", 2L);
        WalletTransaction existing = WalletTransaction.create(other, WalletTransactionType.SETTLEMENT,
                ORDER_ID, PAYOUT_ID, ReferenceType.SETTLEMENT, null, 9_700L, 9_700L);
        when(walletTransactionRepository.findByTypeAndOrderIdAndReferenceIdAndReferenceType(
                WalletTransactionType.SETTLEMENT, ORDER_ID, PAYOUT_ID, ReferenceType.SETTLEMENT))
                .thenReturn(Optional.of(existing));

        assertError(() -> useCase.deposit(WALLET_ID, 9_700L, PAYOUT_ID, ORDER_ID),
                WalletErrorCode.WALLET_ACCESS_DENIED);
    }

    @Test
    @DisplayName("지갑이 없으면 예외가 발생한다")
    void walletNotFound() {
        when(walletRepository.findById(99L)).thenReturn(Optional.empty());

        assertError(() -> useCase.deposit(99L, 9_700L, PAYOUT_ID, ORDER_ID), WalletErrorCode.WALLET_NOT_FOUND);
    }

    @Test
    @DisplayName("필수 값이 없거나 금액이 0 이하이면 예외가 발생한다")
    void invalidInputs() {
        assertError(() -> useCase.deposit(null, 9_700L, PAYOUT_ID, ORDER_ID), WalletErrorCode.WALLET_ID_REQUIRED);
        assertError(() -> useCase.deposit(WALLET_ID, null, PAYOUT_ID, ORDER_ID), WalletErrorCode.WALLET_INVALID_AMOUNT);
        assertError(() -> useCase.deposit(WALLET_ID, 0L, PAYOUT_ID, ORDER_ID), WalletErrorCode.WALLET_INVALID_AMOUNT);
        assertError(() -> useCase.deposit(WALLET_ID, 9_700L, null, ORDER_ID), WalletErrorCode.WALLET_PAYOUT_ID_REQUIRED);
        assertError(() -> useCase.deposit(WALLET_ID, 9_700L, PAYOUT_ID, null), WalletErrorCode.WALLET_ORDER_ID_REQUIRED);
        assertThat(wallet.getBalance()).isZero();
    }
}
