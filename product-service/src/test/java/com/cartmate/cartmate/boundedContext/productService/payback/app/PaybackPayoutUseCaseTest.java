package com.cartmate.cartmate.boundedContext.productService.payback.app;

import com.cartmate.cartmate.boundedContext.productService.payback.domain.PaybackSettlement;
import com.cartmate.cartmate.shared.cart.GroupPurchaseType;
import com.cartmate.cartmate.shared.product.PaybackFunder;
import com.cartmate.cartmate.boundedContext.productService.payback.domain.PaybackPayout;
import com.cartmate.cartmate.boundedContext.productService.payback.domain.PaybackPayoutStatus;
import com.cartmate.cartmate.boundedContext.productService.code.PaybackErrorCode;
import com.cartmate.cartmate.boundedContext.productService.payback.out.PaybackPayoutRepository;
import com.cartmate.cartmate.global.event.DomainEvent;
import com.cartmate.cartmate.global.event.DomainEventPublisher;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.product.event.PaybackRequestedEvent;
import com.cartmate.cartmate.shared.wallet.event.PaybackDepositFailedEvent;
import com.cartmate.cartmate.shared.wallet.event.PaybackDepositedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class PaybackPayoutUseCaseTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 6, 13, 0);

    private final PaybackPayoutRepository repository = mock(PaybackPayoutRepository.class);
    private final List<DomainEvent> published = new ArrayList<>();
    private final DomainEventPublisher publisher = published::add;
    private final PaybackPayoutUseCase useCase = new PaybackPayoutUseCase(repository, publisher);

    @Test
    @DisplayName("지급 줄마다(회원 · 상품 · 부담 주체 순) 지급 행을 만들고 그 id를 paybackId로 공동구매 종류 · id와 함께 요청 이벤트를 발행한다")
    void requestAll() {
        AtomicLong ids = new AtomicLong(100);
        given(repository.save(any())).willAnswer(invocation -> {
            PaybackPayout payout = invocation.getArgument(0);
            ReflectionTestUtils.setField(payout, "id", ids.incrementAndGet());
            return payout;
        });

        useCase.requestAll(GroupPurchaseType.INDIVIDUAL, 7L, List.of(
                new PaybackSettlement.PayoutLine(20L, 1L, PaybackFunder.ADMIN, null, 2, 500L),
                new PaybackSettlement.PayoutLine(10L, 1L, PaybackFunder.SELLER, 77L, 3, 300L),
                new PaybackSettlement.PayoutLine(10L, 1L, PaybackFunder.ADMIN, null, 3, 500L)), NOW);

        assertThat(published).map(PaybackRequestedEvent.class::cast)
                .extracting(PaybackRequestedEvent::paybackId, PaybackRequestedEvent::memberId,
                        PaybackRequestedEvent::amount, PaybackRequestedEvent::groupPurchaseType,
                        PaybackRequestedEvent::groupPurchaseId, PaybackRequestedEvent::attempt)
                .containsExactly(tuple(101L, 10L, 1_500L, GroupPurchaseType.INDIVIDUAL, 7L, 1),
                        tuple(102L, 10L, 900L, GroupPurchaseType.INDIVIDUAL, 7L, 1),
                        tuple(103L, 20L, 1_000L, GroupPurchaseType.INDIVIDUAL, 7L, 1));
        assertThat(published.getFirst().aggregateId()).isEqualTo("10");
    }

    @Test
    @DisplayName("적립 완료 · 실패 결과를 지급 행에 반영한다")
    void applyResults() {
        PaybackPayout paid = payout(1L);
        PaybackPayout failed = payout(2L);

        useCase.applyDeposited(new PaybackDepositedEvent(1L, 10L, 1_000L, 1, NOW.plusMinutes(1)));
        useCase.applyDepositFailed(new PaybackDepositFailedEvent(2L, 20L, "WALLET_NOT_FOUND", false, 1, NOW.plusMinutes(1)));

        assertThat(paid.getStatus()).isEqualTo(PaybackPayoutStatus.PAID);
        assertThat(failed.getStatus()).isEqualTo(PaybackPayoutStatus.FAILED);
    }

    @Test
    @DisplayName("회원이나 금액이 지급 행과 다른 적립 결과는 반영하지 않는다")
    void mismatchedDeposit() {
        PaybackPayout payout = payout(1L);

        useCase.applyDeposited(new PaybackDepositedEvent(1L, 10L, 999L, 1, NOW));
        useCase.applyDeposited(new PaybackDepositedEvent(1L, 99L, 1_000L, 1, NOW));

        assertThat(payout.getStatus()).isEqualTo(PaybackPayoutStatus.REQUESTED);
    }

    @Test
    @DisplayName("앞선 시도의 늦은 실패는 지금 요청에 반영하지 않는다")
    void staleFailure() {
        PaybackPayout payout = payout(1L);
        payout.markFailed("WALLET_BUSY", true, 1, NOW);
        useCase.retryWaiting(1L, NOW.plusMinutes(1));

        useCase.applyDepositFailed(new PaybackDepositFailedEvent(1L, 10L, "WALLET_BUSY", true, 1, NOW.plusMinutes(2)));

        assertThat(payout.getStatus()).isEqualTo(PaybackPayoutStatus.REQUESTED);
        assertThat(payout.getAttempt()).isEqualTo(2);
    }

    @Test
    @DisplayName("모르는 paybackId의 결과는 무시한다")
    void unknownPayback() {
        given(repository.findByIdForUpdate(any())).willReturn(Optional.empty());

        useCase.applyDeposited(new PaybackDepositedEvent(404L, 10L, 1_000L, 1, NOW));
        useCase.applyDepositFailed(new PaybackDepositFailedEvent(404L, 10L, "X", true, 1, NOW));

        assertThat(published).isEmpty();
    }

    @Test
    @DisplayName("자동 재요청은 대기 상태만 같은 paybackId로 다시 요청하고, 이미 처리됐으면 건너뛴다")
    void retryWaiting() {
        PaybackPayout waiting = payout(1L);
        waiting.markFailed("WALLET_BUSY", true, waiting.getAttempt(), NOW);
        PaybackPayout requested = payout(2L);

        useCase.retryWaiting(1L, NOW.plusMinutes(1));
        useCase.retryWaiting(2L, NOW.plusMinutes(1));

        assertThat(published).map(PaybackRequestedEvent.class::cast)
                .extracting(PaybackRequestedEvent::paybackId, PaybackRequestedEvent::attempt)
                .containsExactly(tuple(1L, 2));
        assertThat(requested.getAttempt()).isEqualTo(1);
    }

    @Test
    @DisplayName("어드민 재요청은 실패로 확정된 건만 받고, 없거나 실패가 아니면 거절한다")
    void retryFailed() {
        PaybackPayout failed = payout(1L);
        failed.markFailed("WALLET_NOT_FOUND", false, failed.getAttempt(), NOW);
        payout(2L);
        given(repository.findByIdForUpdate(404L)).willReturn(Optional.empty());

        useCase.retryFailed(1L, NOW.plusDays(1));

        assertThat(failed.getStatus()).isEqualTo(PaybackPayoutStatus.REQUESTED);
        assertThat(published).hasSize(1);
        assertThatThrownBy(() -> useCase.retryFailed(2L, NOW))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(PaybackErrorCode.PAYBACK_NOT_FAILED.getMsg());
        assertThatThrownBy(() -> useCase.retryFailed(404L, NOW))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(PaybackErrorCode.PAYBACK_NOT_FOUND.getMsg());
    }

    private PaybackPayout payout(Long id) {
        PaybackPayout payout = PaybackPayout.request(GroupPurchaseType.INDIVIDUAL, 7L, adminLine(id * 10, 1_000L), NOW);
        ReflectionTestUtils.setField(payout, "id", id);
        given(repository.findByIdForUpdate(id)).willReturn(Optional.of(payout));
        return payout;
    }

    private static PaybackSettlement.PayoutLine adminLine(Long memberId, long amount) {
        return new PaybackSettlement.PayoutLine(memberId, 1L, PaybackFunder.ADMIN, null, 1, amount);
    }
}
