package com.cartmate.cartmate.boundedContext.payout.app;

import com.cartmate.cartmate.boundedContext.payout.domain.Payout;
import com.cartmate.cartmate.boundedContext.payout.domain.PayoutSeller;
import com.cartmate.cartmate.boundedContext.payout.domain.PayoutStatus;
import com.cartmate.cartmate.boundedContext.payout.exception.PayoutErrorCode;
import com.cartmate.cartmate.boundedContext.payout.out.PayoutRepository;
import com.cartmate.cartmate.boundedContext.payout.out.PayoutSellerRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.wallet.WalletSettlementPort;
import com.cartmate.cartmate.shared.wallet.dto.SettleAndDepositRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayoutPayUseCaseTest {

    private static final LocalDateTime CONFIRMED_AT = LocalDateTime.of(2026, 10, 19, 13, 30);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 15, 0, 10);

    private final PayoutRepository payoutRepository = mock(PayoutRepository.class);
    private final PayoutSellerRepository payoutSellerRepository = mock(PayoutSellerRepository.class);
    private final WalletSettlementPort walletSettlementPort = mock(WalletSettlementPort.class);
    private PayoutPayUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new PayoutPayUseCase(payoutRepository, payoutSellerRepository, walletSettlementPort);
        when(payoutSellerRepository.findById(10L)).thenReturn(Optional.of(PayoutSeller.create(10L, 20L)));
    }

    private Payout payout(long salesAmount) {
        Payout payout = Payout.create(10L, 100L, 1000L, salesAmount, CONFIRMED_AT);
        ReflectionTestUtils.setField(payout, "id", 7L);
        when(payoutRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(payout));
        return payout;
    }

    @Test
    @DisplayName("판매자 회원 ID와 금액을 담아 입금을 요청하고 정산을 지급 완료로 바꾼다")
    void pay() {
        Payout payout = payout(10_000L);

        useCase.pay(7L, NOW);

        verify(walletSettlementPort).settleAndDeposit(new SettleAndDepositRequest(7L, 100L, 20L, 10_000L, 9_700L));
        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.COMPLETED);
        assertThat(payout.getPaidAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("정산 금액이 0원이면 입금 없이 완료 처리만 한다")
    void zeroAmountCompletesWithoutDeposit() {
        Payout payout = payout(0L);

        useCase.pay(7L, NOW);

        verify(walletSettlementPort, never()).settleAndDeposit(any());
        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.COMPLETED);
        assertThat(payout.getPaidAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("이미 처리된 정산은 아무것도 하지 않는다")
    void alreadyCompletedIsIgnored() {
        Payout payout = payout(10_000L);
        payout.complete(NOW.minusDays(1));

        useCase.pay(7L, NOW);

        verify(walletSettlementPort, never()).settleAndDeposit(any());
        assertThat(payout.getPaidAt()).isEqualTo(NOW.minusDays(1));
    }

    @Test
    @DisplayName("입금이 실패하면 예외가 전파되고 정산은 대기 상태로 남는다")
    void depositFailureKeepsPending() {
        Payout payout = payout(10_000L);
        doThrow(new IllegalStateException("입금 실패")).when(walletSettlementPort).settleAndDeposit(any());

        assertThatThrownBy(() -> useCase.pay(7L, NOW)).isInstanceOf(IllegalStateException.class);

        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.PENDING);
        assertThat(payout.getPaidAt()).isNull();
    }

    @Test
    @DisplayName("복제된 판매자 정보가 없으면 예외가 발생하고 입금하지 않는다")
    void sellerNotFound() {
        Payout payout = payout(10_000L);
        when(payoutSellerRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.pay(7L, NOW))
                .isInstanceOfSatisfying(ServiceException.class, e -> assertThat(e.getResultCode())
                        .isEqualTo(PayoutErrorCode.PAYOUT_SELLER_NOT_FOUND.resultCode()));
        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.PENDING);
        verify(walletSettlementPort, never()).settleAndDeposit(any());
    }

    @Test
    @DisplayName("없는 정산은 예외가 발생한다")
    void notFound() {
        when(payoutRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.pay(99L, NOW))
                .isInstanceOfSatisfying(ServiceException.class, e -> assertThat(e.getResultCode())
                        .isEqualTo(PayoutErrorCode.PAYOUT_NOT_FOUND.resultCode()));
    }
}
