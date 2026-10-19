package com.cartmate.cartmate.boundedContext.wallet.domain;

import com.cartmate.cartmate.boundedContext.wallet.exception.WalletErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HoldingSettlementAllocatorTest {

    @Test
    @DisplayName("남은 금액 비율대로 나눠서 꺼낸다")
    void proportional() {
        assertThat(HoldingSettlementAllocator.allocate(List.of(6_000L, 4_000L), 10_000L))
                .containsExactly(6_000L, 4_000L);
        assertThat(HoldingSettlementAllocator.allocate(List.of(6_000L, 4_000L), 5_000L))
                .containsExactly(3_000L, 2_000L);
    }

    @Test
    @DisplayName("홀딩이 하나면 그 홀딩에서 전부 꺼낸다")
    void single() {
        assertThat(HoldingSettlementAllocator.allocate(List.of(10_000L), 3_000L)).containsExactly(3_000L);
    }

    @Test
    @DisplayName("나누어떨어지지 않는 금액은 남은 금액이 큰 홀딩부터 1원씩 더하고 합계는 정산 금액과 같다")
    void remainder() {
        List<Long> shares = HoldingSettlementAllocator.allocate(List.of(1_000L, 1_000L, 1_000L), 100L);

        assertThat(shares.stream().mapToLong(Long::longValue).sum()).isEqualTo(100L);
        assertThat(shares).containsExactly(34L, 33L, 33L);
    }

    @Test
    @DisplayName("몫은 홀딩의 남은 금액을 넘지 않는다")
    void neverExceedsRemaining() {
        List<Long> shares = HoldingSettlementAllocator.allocate(List.of(1L, 1L, 1L), 2L);

        assertThat(shares.stream().mapToLong(Long::longValue).sum()).isEqualTo(2L);
        assertThat(shares).allSatisfy(share -> assertThat(share).isLessThanOrEqualTo(1L));
    }

    @Test
    @DisplayName("남은 금액이 이미 0인 홀딩에서는 꺼내지 않는다")
    void zeroRemainingGetsNothing() {
        assertThat(HoldingSettlementAllocator.allocate(List.of(0L, 5_000L), 3_000L)).containsExactly(0L, 3_000L);
    }

    @Test
    @DisplayName("남은 금액의 합계가 정산 금액보다 작으면 예외가 발생한다")
    void insufficient() {
        assertThatThrownBy(() -> HoldingSettlementAllocator.allocate(List.of(1_000L, 2_000L), 3_001L))
                .isInstanceOfSatisfying(ServiceException.class, e -> assertThat(e.getResultCode())
                        .isEqualTo(WalletErrorCode.WALLET_HOLDING_INSUFFICIENT.resultCode()));
    }

    @Test
    @DisplayName("정산 금액이 0 이하이면 예외가 발생한다")
    void invalidAmount() {
        assertThatThrownBy(() -> HoldingSettlementAllocator.allocate(List.of(1_000L), 0L))
                .isInstanceOfSatisfying(ServiceException.class, e -> assertThat(e.getResultCode())
                        .isEqualTo(WalletErrorCode.WALLET_INVALID_AMOUNT.resultCode()));
    }
}
