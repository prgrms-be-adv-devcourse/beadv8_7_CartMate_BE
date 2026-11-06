package com.cartmate.cartmate.boundedContext.orderService.order.domain.personal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class PaybackSettlementTest {

    @Test
    @DisplayName("결제 완료 주문이 모두 구매확정이면 구매확정한 서로 다른 구매자 수를 센다")
    void allConfirmed() {
        List<Order> orders = List.of(order(1L, 10L, OrderStatus.PAID), order(2L, 10L, OrderStatus.PAID),
                order(3L, 20L, OrderStatus.PAID));

        assertThat(PaybackSettlement.confirmedBuyerCount(orders, Set.of(1L, 2L, 3L))).hasValue(2);
    }

    @Test
    @DisplayName("구매확정 안 된 결제 완료 주문이나 결제 대기 주문이 남으면 판정하지 않는다")
    void notSettled() {
        assertThat(PaybackSettlement.confirmedBuyerCount(
                List.of(order(1L, 10L, OrderStatus.PAID), order(2L, 20L, OrderStatus.PAID)), Set.of(1L))).isEmpty();
        assertThat(PaybackSettlement.confirmedBuyerCount(
                List.of(order(3L, 30L, OrderStatus.PAYMENT_PENDING)), Set.of())).isEmpty();
    }

    @Test
    @DisplayName("취소된 주문(구매자 취소 · 판매 중지 · 공동구매 종료 자동 취소)은 끝난 것으로 보고 인원에서 뺀다")
    void canceledExcluded() {
        List<Order> orders = List.of(order(1L, 10L, OrderStatus.PAID), order(2L, 20L, OrderStatus.CANCELED));

        assertThat(PaybackSettlement.confirmedBuyerCount(orders, Set.of(1L))).hasValue(1);
    }

    @Test
    @DisplayName("주문이 없으면 0명으로 판정할 수 있고, 구매확정 인원이 기준 이상이면 달성이다")
    void noOrdersAndJudgment() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 20, 3, 0);

        assertThat(PaybackSettlement.confirmedBuyerCount(List.of(), Set.of())).hasValue(0);
        assertThat(IndividualGroupPurchasePayback.judge(1L, 10, 1_000L, 10, now).isReached()).isTrue();
        assertThat(IndividualGroupPurchasePayback.judge(1L, 10, 1_000L, 9, now).isReached()).isFalse();
    }

    private static Order order(Long id, Long buyer, OrderStatus status) {
        Order order = mock(Order.class);
        given(order.getId()).willReturn(id);
        given(order.getBuyerMemberId()).willReturn(buyer);
        given(order.getStatus()).willReturn(status);
        return order;
    }
}
