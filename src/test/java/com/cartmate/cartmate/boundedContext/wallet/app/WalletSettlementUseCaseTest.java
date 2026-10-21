package com.cartmate.cartmate.boundedContext.wallet.app;

import com.cartmate.cartmate.boundedContext.wallet.domain.Holding;
import com.cartmate.cartmate.shared.wallet.WalletPurchaseType;
import com.cartmate.cartmate.boundedContext.wallet.domain.HoldingSettlement;
import com.cartmate.cartmate.boundedContext.wallet.domain.HoldingStatus;
import com.cartmate.cartmate.boundedContext.wallet.domain.ReferenceType;
import com.cartmate.cartmate.boundedContext.wallet.domain.Wallet;
import com.cartmate.cartmate.boundedContext.wallet.domain.WalletTransactionType;
import com.cartmate.cartmate.boundedContext.wallet.exception.WalletErrorCode;
import com.cartmate.cartmate.boundedContext.wallet.out.HoldingRepository;
import com.cartmate.cartmate.boundedContext.wallet.out.HoldingSettlementRepository;
import com.cartmate.cartmate.boundedContext.wallet.out.WalletRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.wallet.dto.SettleAndDepositRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WalletSettlementUseCaseTest {

    private static final long SELLER_MEMBER_ID = 20L;
    private static final long PAYOUT_ID = 300L;
    private static final long ORDER_ID = 100L;

    private final WalletRepository walletRepository = mock(WalletRepository.class);
    private final HoldingRepository holdingRepository = mock(HoldingRepository.class);
    private final HoldingSettlementRepository holdingSettlementRepository = mock(HoldingSettlementRepository.class);
    private final PlatformWalletProvider platformWalletProvider = mock(PlatformWalletProvider.class);
    private final LedgerRecorder ledgerRecorder = mock(LedgerRecorder.class);
    private WalletSettlementUseCase useCase;
    private Wallet sellerWallet;
    private Wallet platformWallet;

    @BeforeEach
    void setUp() {
        useCase = new WalletSettlementUseCase(walletRepository, holdingRepository, holdingSettlementRepository,
                platformWalletProvider, ledgerRecorder);

        sellerWallet = Wallet.createWallet(SELLER_MEMBER_ID);
        platformWallet = Wallet.createWallet(1L);
        when(walletRepository.findByMemberId(SELLER_MEMBER_ID)).thenReturn(Optional.of(sellerWallet));
        when(platformWalletProvider.getPlatformWallet()).thenReturn(platformWallet);
        when(holdingSettlementRepository.findAllByPayoutId(PAYOUT_ID)).thenReturn(List.of());
    }

    private Holding holding(long id, long paymentId, long amount, long alreadySettled) {
        Holding holding = Holding.create(Wallet.createWallet(id + 1_000), ORDER_ID, WalletPurchaseType.INDIVIDUAL_GROUP, amount, paymentId);
        ReflectionTestUtils.setField(holding, "id", id);
        when(holdingSettlementRepository.sumAmountByHoldingId(id)).thenReturn(alreadySettled);
        return holding;
    }

    private void holdings(Holding... holdings) {
        when(holdingRepository.findAllByOrderIdAndPurchaseTypeForUpdate(ORDER_ID, WalletPurchaseType.INDIVIDUAL_GROUP)).thenReturn(List.of(holdings));
    }

    private SettleAndDepositRequest request(long salesAmount, long payoutAmount) {
        return new SettleAndDepositRequest(PAYOUT_ID, ORDER_ID, WalletPurchaseType.INDIVIDUAL_GROUP, SELLER_MEMBER_ID, salesAmount, payoutAmount);
    }

    private void assertError(Runnable action, WalletErrorCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ServiceException.class,
                        e -> assertThat(e.getResultCode()).isEqualTo(code.resultCode()));
    }

    @Test
    @DisplayName("홀딩에서 판매 금액을 꺼내 판매자에게 정산금을, 플랫폼 지갑에 수수료를 입금한다")
    void settleSingleHolding() {
        Holding holding = holding(1L, 200L, 10_000L, 0L);
        holdings(holding);

        useCase.settleAndDeposit(request(10_000L, 9_700L));

        ArgumentCaptor<HoldingSettlement> captor = ArgumentCaptor.forClass(HoldingSettlement.class);
        verify(holdingSettlementRepository).save(captor.capture());
        assertThat(captor.getValue().getHolding()).isSameAs(holding);
        assertThat(captor.getValue().getPayoutId()).isEqualTo(PAYOUT_ID);
        assertThat(captor.getValue().getAmount()).isEqualTo(10_000L);
        assertThat(holding.getStatus()).isEqualTo(HoldingStatus.SETTLED);
        assertThat(sellerWallet.getBalance()).isEqualTo(9_700L);
        assertThat(platformWallet.getBalance()).isEqualTo(300L);
        verify(ledgerRecorder).record(sellerWallet, WalletTransactionType.SETTLEMENT, ORDER_ID,
                PAYOUT_ID, ReferenceType.SETTLEMENT, null, 9_700L);
        verify(ledgerRecorder).record(platformWallet, WalletTransactionType.FEE, ORDER_ID,
                PAYOUT_ID, ReferenceType.SETTLEMENT, null, 300L);
    }

    @Test
    @DisplayName("홀딩이 여러 개이면 남은 금액 비율로 나눠 꺼낸다")
    void settleMultipleHoldings() {
        Holding first = holding(1L, 200L, 6_000L, 0L);
        Holding second = holding(2L, 201L, 4_000L, 0L);
        holdings(first, second);

        useCase.settleAndDeposit(request(5_000L, 4_850L));

        ArgumentCaptor<HoldingSettlement> captor = ArgumentCaptor.forClass(HoldingSettlement.class);
        verify(holdingSettlementRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(HoldingSettlement::getAmount).containsExactly(3_000L, 2_000L);
        assertThat(first.getStatus()).isEqualTo(HoldingStatus.RESERVED);
        assertThat(second.getStatus()).isEqualTo(HoldingStatus.RESERVED);
        assertThat(sellerWallet.getBalance()).isEqualTo(4_850L);
        assertThat(platformWallet.getBalance()).isEqualTo(150L);
    }

    @Test
    @DisplayName("앞선 정산으로 이미 꺼낸 금액은 남은 금액에서 제외하고 모두 소진되면 정산 완료가 된다")
    void settleAfterPreviousSettlement() {
        Holding holding = holding(1L, 200L, 10_000L, 6_000L);
        holdings(holding);

        useCase.settleAndDeposit(request(4_000L, 3_880L));

        assertThat(holding.getStatus()).isEqualTo(HoldingStatus.SETTLED);
        assertThat(sellerWallet.getBalance()).isEqualTo(3_880L);
    }

    @Test
    @DisplayName("수수료가 없으면 플랫폼 지갑에는 입금하지 않는다")
    void noFee() {
        holdings(holding(1L, 200L, 10_000L, 0L));

        useCase.settleAndDeposit(request(30L, 30L));

        assertThat(sellerWallet.getBalance()).isEqualTo(30L);
        verify(platformWalletProvider, never()).getPlatformWallet();
        verify(ledgerRecorder, never()).record(any(), eq(WalletTransactionType.FEE), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("같은 정산 ID로 다시 요청하면 아무것도 바꾸지 않는다")
    void duplicateIsIdempotent() {
        Holding holding = holding(1L, 200L, 10_000L, 0L);
        HoldingSettlement done = HoldingSettlement.create(holding, PAYOUT_ID, 10_000L);
        when(holdingSettlementRepository.findAllByPayoutId(PAYOUT_ID)).thenReturn(List.of(done));

        useCase.settleAndDeposit(request(10_000L, 9_700L));

        assertThat(sellerWallet.getBalance()).isZero();
        verify(holdingSettlementRepository, never()).save(any());
        verify(ledgerRecorder, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("같은 정산 ID에 금액이 다르면 예외가 발생한다")
    void duplicateWithDifferentAmount() {
        Holding holding = holding(1L, 200L, 10_000L, 0L);
        HoldingSettlement done = HoldingSettlement.create(holding, PAYOUT_ID, 10_000L);
        when(holdingSettlementRepository.findAllByPayoutId(PAYOUT_ID)).thenReturn(List.of(done));

        assertError(() -> useCase.settleAndDeposit(request(8_000L, 7_760L)),
                WalletErrorCode.WALLET_TRANSACTION_AMOUNT_MISMATCH);
    }

    @Test
    @DisplayName("홀딩의 남은 금액이 모자라면 예외가 발생하고 입금하지 않는다")
    void insufficientHolding() {
        holdings(holding(1L, 200L, 10_000L, 8_000L));

        assertError(() -> useCase.settleAndDeposit(request(3_000L, 2_910L)),
                WalletErrorCode.WALLET_HOLDING_INSUFFICIENT);
        assertThat(sellerWallet.getBalance()).isZero();
        verify(holdingSettlementRepository, never()).save(any());
    }

    @Test
    @DisplayName("정산할 홀딩이 없으면 예외가 발생한다")
    void noHolding() {
        holdings();

        assertError(() -> useCase.settleAndDeposit(request(1_000L, 970L)), WalletErrorCode.WALLET_HOLDING_NOT_FOUND);
    }

    @Test
    @DisplayName("이미 정산이 끝났거나 취소된 홀딩은 대상에서 제외한다")
    void skipClosedHoldings() {
        Holding closed = holding(1L, 200L, 5_000L, 0L);
        closed.addRefund(5_000L, 0L);
        Holding open = holding(2L, 201L, 5_000L, 0L);
        holdings(closed, open);

        useCase.settleAndDeposit(request(2_000L, 1_940L));

        ArgumentCaptor<HoldingSettlement> captor = ArgumentCaptor.forClass(HoldingSettlement.class);
        verify(holdingSettlementRepository).save(captor.capture());
        assertThat(captor.getValue().getHolding()).isSameAs(open);
        assertThat(captor.getValue().getAmount()).isEqualTo(2_000L);
    }

    @Test
    @DisplayName("판매자 지갑이 없으면 예외가 발생한다")
    void sellerWalletNotFound() {
        when(walletRepository.findByMemberId(SELLER_MEMBER_ID)).thenReturn(Optional.empty());

        assertError(() -> useCase.settleAndDeposit(request(1_000L, 970L)), WalletErrorCode.WALLET_NOT_FOUND);
    }

    @Test
    @DisplayName("필수 값이 없거나 금액이 올바르지 않으면 예외가 발생한다")
    void invalidRequest() {
        assertError(() -> useCase.settleAndDeposit(new SettleAndDepositRequest(PAYOUT_ID, ORDER_ID, WalletPurchaseType.INDIVIDUAL_GROUP, null, 1_000L, 970L)),
                WalletErrorCode.WALLET_MEMBER_ID_REQUIRED);
        assertError(() -> useCase.settleAndDeposit(new SettleAndDepositRequest(null, ORDER_ID, WalletPurchaseType.INDIVIDUAL_GROUP, SELLER_MEMBER_ID, 1_000L, 970L)),
                WalletErrorCode.WALLET_PAYOUT_ID_REQUIRED);
        assertError(() -> useCase.settleAndDeposit(new SettleAndDepositRequest(PAYOUT_ID, null, WalletPurchaseType.INDIVIDUAL_GROUP, SELLER_MEMBER_ID, 1_000L, 970L)),
                WalletErrorCode.WALLET_ORDER_ID_REQUIRED);
        assertError(() -> useCase.settleAndDeposit(new SettleAndDepositRequest(PAYOUT_ID, ORDER_ID, null, SELLER_MEMBER_ID, 1_000L, 970L)),
                WalletErrorCode.WALLET_PURCHASE_TYPE_REQUIRED);
        assertError(() -> useCase.settleAndDeposit(request(0L, 0L)), WalletErrorCode.WALLET_INVALID_AMOUNT);
        assertError(() -> useCase.settleAndDeposit(request(1_000L, 0L)), WalletErrorCode.WALLET_INVALID_AMOUNT);
        assertError(() -> useCase.settleAndDeposit(request(1_000L, 1_001L)), WalletErrorCode.WALLET_INVALID_AMOUNT);
    }

    @Test
    @DisplayName("주문 ID가 같아도 구매 유형이 다르면 다른 유형의 홀딩에서는 꺼내지 않는다")
    void settleOnlyHoldingsOfSamePurchaseType() {
        Holding individual = holding(1L, 200L, 10_000L, 0L);
        Holding shared = Holding.create(Wallet.createWallet(2_000L), ORDER_ID, WalletPurchaseType.SHARED_GROUP, 30_000L, 300L);
        ReflectionTestUtils.setField(shared, "id", 2L);
        when(holdingSettlementRepository.sumAmountByHoldingId(2L)).thenReturn(0L);
        holdings(individual);
        when(holdingRepository.findAllByOrderIdAndPurchaseTypeForUpdate(ORDER_ID, WalletPurchaseType.SHARED_GROUP))
                .thenReturn(List.of(shared));

        useCase.settleAndDeposit(new SettleAndDepositRequest(
                PAYOUT_ID, ORDER_ID, WalletPurchaseType.SHARED_GROUP, SELLER_MEMBER_ID, 30_000L, 29_100L));

        ArgumentCaptor<HoldingSettlement> captor = ArgumentCaptor.forClass(HoldingSettlement.class);
        verify(holdingSettlementRepository).save(captor.capture());
        assertThat(captor.getValue().getHolding()).isSameAs(shared);
        assertThat(captor.getValue().getAmount()).isEqualTo(30_000L);
        assertThat(individual.getStatus()).isEqualTo(HoldingStatus.RESERVED);
        assertThat(shared.getStatus()).isEqualTo(HoldingStatus.SETTLED);
    }
}
