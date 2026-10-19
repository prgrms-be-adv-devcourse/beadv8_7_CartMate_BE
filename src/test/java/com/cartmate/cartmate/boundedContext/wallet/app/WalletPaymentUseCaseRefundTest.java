package com.cartmate.cartmate.boundedContext.wallet.app;

import com.cartmate.cartmate.boundedContext.wallet.domain.Holding;
import com.cartmate.cartmate.boundedContext.wallet.domain.HoldingStatus;
import com.cartmate.cartmate.boundedContext.wallet.domain.ReferenceType;
import com.cartmate.cartmate.boundedContext.wallet.domain.Wallet;
import com.cartmate.cartmate.boundedContext.wallet.domain.WalletTransaction;
import com.cartmate.cartmate.boundedContext.wallet.domain.WalletTransactionType;
import com.cartmate.cartmate.boundedContext.wallet.exception.WalletErrorCode;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WalletPaymentUseCaseRefundTest {

    private static final long WALLET_ID = 1L;
    private static final long ORDER_ID = 100L;
    private static final long PAYMENT_ID = 200L;

    private final WalletRepository walletRepository = mock(WalletRepository.class);
    private final WalletTransactionRepository walletTransactionRepository = mock(WalletTransactionRepository.class);
    private final HoldingRepository holdingRepository = mock(HoldingRepository.class);
    private final HoldingSettlementRepository holdingSettlementRepository = mock(HoldingSettlementRepository.class);
    private final LedgerRecorder ledgerRecorder = mock(LedgerRecorder.class);
    private WalletPaymentUseCase useCase;
    private Wallet wallet;
    private Holding holding;

    @BeforeEach
    void setUp() {
        useCase = new WalletPaymentUseCase(walletRepository, walletTransactionRepository, holdingRepository,
                holdingSettlementRepository, ledgerRecorder);

        wallet = Wallet.createWallet(10L);
        ReflectionTestUtils.setField(wallet, "id", WALLET_ID);
        wallet.charge(10_000L);
        wallet.use(10_000L);

        holding = Holding.create(wallet, ORDER_ID, 10_000L, PAYMENT_ID);

        when(walletRepository.findById(WALLET_ID)).thenReturn(Optional.of(wallet));
        when(holdingRepository.findByOrderIdAndPaymentId(ORDER_ID, PAYMENT_ID)).thenReturn(Optional.of(holding));
        when(walletTransactionRepository.findByTypeAndOrderIdAndReferenceIdAndReferenceType(
                any(), any(), any(), any())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("부분 환불하면 잔액이 복구되고 홀딩은 RESERVED를 유지한다")
    void partialRefund() {
        useCase.refund(WALLET_ID, 3_000L, PAYMENT_ID, ORDER_ID, 1L);

        assertThat(wallet.getBalance()).isEqualTo(3_000L);
        assertThat(holding.getRefundedAmount()).isEqualTo(3_000L);
        assertThat(holding.getStatus()).isEqualTo(HoldingStatus.RESERVED);
        verify(ledgerRecorder).record(wallet, WalletTransactionType.REFUND, ORDER_ID,
                1L, ReferenceType.REFUND, null, 3_000L);
    }

    @Test
    @DisplayName("같은 결제를 refundId를 달리해 여러 번 부분 환불하고 전액이 되면 홀딩이 취소된다")
    void multiplePartialRefunds() {
        useCase.refund(WALLET_ID, 3_000L, PAYMENT_ID, ORDER_ID, 1L);
        useCase.refund(WALLET_ID, 7_000L, PAYMENT_ID, ORDER_ID, 2L);

        assertThat(wallet.getBalance()).isEqualTo(10_000L);
        assertThat(holding.getRefundedAmount()).isEqualTo(10_000L);
        assertThat(holding.getStatus()).isEqualTo(HoldingStatus.CANCELED);
    }

    @Test
    @DisplayName("홀딩 잔여 금액을 초과해 환불하면 예외가 발생한다")
    void refundExceedsRemaining() {
        useCase.refund(WALLET_ID, 8_000L, PAYMENT_ID, ORDER_ID, 1L);

        assertThatThrownBy(() -> useCase.refund(WALLET_ID, 3_000L, PAYMENT_ID, ORDER_ID, 2L))
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getResultCode()).isEqualTo(WalletErrorCode.WALLET_HOLDING_REFUND_EXCEEDED.resultCode()));
        assertThat(wallet.getBalance()).isEqualTo(8_000L);
    }

    @Test
    @DisplayName("같은 refundId로 재요청하면 아무것도 변경하지 않는다")
    void duplicateRefundIsIdempotent() {
        WalletTransaction existing = WalletTransaction.create(wallet, WalletTransactionType.REFUND,
                ORDER_ID, 1L, ReferenceType.REFUND, null, 3_000L, 3_000L);
        when(walletTransactionRepository.findByTypeAndOrderIdAndReferenceIdAndReferenceType(
                WalletTransactionType.REFUND, ORDER_ID, 1L, ReferenceType.REFUND)).thenReturn(Optional.of(existing));

        useCase.refund(WALLET_ID, 3_000L, PAYMENT_ID, ORDER_ID, 1L);

        assertThat(holding.getRefundedAmount()).isZero();
        verify(ledgerRecorder, never()).record(any(), any(), any(), any(), any(), isNull(), any());
    }

    @Test
    @DisplayName("같은 refundId에 금액이 다르면 예외가 발생한다")
    void duplicateRefundWithDifferentAmount() {
        WalletTransaction existing = WalletTransaction.create(wallet, WalletTransactionType.REFUND,
                ORDER_ID, 1L, ReferenceType.REFUND, null, 3_000L, 3_000L);
        when(walletTransactionRepository.findByTypeAndOrderIdAndReferenceIdAndReferenceType(
                WalletTransactionType.REFUND, ORDER_ID, 1L, ReferenceType.REFUND)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> useCase.refund(WALLET_ID, 4_000L, PAYMENT_ID, ORDER_ID, 1L))
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getResultCode()).isEqualTo(WalletErrorCode.WALLET_TRANSACTION_AMOUNT_MISMATCH.resultCode()));
    }

    @Test
    @DisplayName("refundId가 없으면 예외가 발생한다")
    void refundIdRequired() {
        assertThatThrownBy(() -> useCase.refund(WALLET_ID, 3_000L, PAYMENT_ID, ORDER_ID, null))
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getResultCode()).isEqualTo(WalletErrorCode.WALLET_REFUND_ID_REQUIRED.resultCode()));
    }

    @Test
    @DisplayName("정산으로 꺼낸 금액을 제외한 남은 금액까지만 환불할 수 있다")
    void refundLimitedBySettledAmount() {
        when(holdingSettlementRepository.sumAmountByHoldingId(any())).thenReturn(4_000L);

        assertThatThrownBy(() -> useCase.refund(WALLET_ID, 7_000L, PAYMENT_ID, ORDER_ID, 1L))
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getResultCode()).isEqualTo(WalletErrorCode.WALLET_HOLDING_REFUND_EXCEEDED.resultCode()));
        assertThat(wallet.getBalance()).isZero();
    }

    @Test
    @DisplayName("정산된 금액이 있는 홀딩의 남은 금액을 모두 환불하면 홀딩은 정산 완료가 된다")
    void refundRemainingAfterSettlement() {
        when(holdingSettlementRepository.sumAmountByHoldingId(any())).thenReturn(4_000L);

        useCase.refund(WALLET_ID, 6_000L, PAYMENT_ID, ORDER_ID, 1L);

        assertThat(wallet.getBalance()).isEqualTo(6_000L);
        assertThat(holding.getStatus()).isEqualTo(HoldingStatus.SETTLED);
    }
}
