package com.cartmate.cartmate.boundedContext.orderService.cart.domain.matching;

import com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual.IndividualGroupPurchaseOrderReplicaStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual.IndividualGroupPurchaseOrderReplicaStatus.CANCELED;
import static com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual.IndividualGroupPurchaseOrderReplicaStatus.PAID;
import static com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual.IndividualGroupPurchaseOrderReplicaStatus.PENDING;
import static org.assertj.core.api.Assertions.assertThat;

class PaybackSettlementTest {

    @Test
    @DisplayName("결제 완료 주문이 모두 구매확정이면 구매확정한 서로 다른 구매자 수를 센다")
    void allConfirmed() {
        List<PaybackSettlement.Order> orders = List.of(order(1L, 10L, PAID), order(2L, 10L, PAID), order(3L, 20L, PAID));

        assertThat(PaybackSettlement.confirmedBuyerCount(orders, Map.of(
                1L, PaybackOrderOutcomeType.CONFIRMED,
                2L, PaybackOrderOutcomeType.CONFIRMED,
                3L, PaybackOrderOutcomeType.CONFIRMED))).hasValue(2);
    }

    @Test
    @DisplayName("구매확정도 취소도 아닌 주문이 하나라도 남으면 아직 판정하지 않는다")
    void notSettled() {
        List<PaybackSettlement.Order> orders = List.of(order(1L, 10L, PAID), order(2L, 20L, PAID));

        assertThat(PaybackSettlement.confirmedBuyerCount(orders, Map.of(1L, PaybackOrderOutcomeType.CONFIRMED)))
                .isEmpty();
        assertThat(PaybackSettlement.confirmedBuyerCount(List.of(order(3L, 30L, PENDING)), Map.of())).isEmpty();
    }

    @Test
    @DisplayName("취소된 주문은 끝난 것으로 보되 인원에 넣지 않는다 (읽기 모델 취소 · 직접 받은 취소 모두)")
    void canceledExcluded() {
        List<PaybackSettlement.Order> orders = List.of(
                order(1L, 10L, PAID), order(2L, 20L, CANCELED), order(3L, 30L, PAID));

        assertThat(PaybackSettlement.confirmedBuyerCount(orders, Map.of(
                1L, PaybackOrderOutcomeType.CONFIRMED,
                3L, PaybackOrderOutcomeType.CANCELED))).hasValue(1);
    }

    @Test
    @DisplayName("구매확정 기록이 있으면 읽기 모델이 아직 결제 대기여도 구매자로 센다")
    void confirmedBeforeReplicaCatchesUp() {
        assertThat(PaybackSettlement.confirmedBuyerCount(List.of(order(1L, 10L, PENDING)),
                Map.of(1L, PaybackOrderOutcomeType.CONFIRMED))).hasValue(1);
    }

    @Test
    @DisplayName("주문이 하나도 없으면 0명으로 판정할 수 있다")
    void noOrders() {
        assertThat(PaybackSettlement.confirmedBuyerCount(List.of(), Map.of())).hasValue(0);
    }

    @Test
    @DisplayName("구매확정 인원이 기준 이상이면 달성이다")
    void judgment() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 20, 3, 0);

        assertThat(PaybackJudgment.judge(1L, 10, 1_000L, 10, now).isReached()).isTrue();
        assertThat(PaybackJudgment.judge(1L, 10, 1_000L, 9, now).isReached()).isFalse();
    }

    private static PaybackSettlement.Order order(Long orderId, Long buyer, IndividualGroupPurchaseOrderReplicaStatus status) {
        return new PaybackSettlement.Order(orderId, buyer, status);
    }
}
