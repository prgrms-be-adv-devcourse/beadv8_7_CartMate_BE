package com.cartmate.cartmate.boundedContext.orderService.order.domain.personal;

import com.cartmate.cartmate.boundedContext.orderService.order.exception.OrderErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

class IndividualGroupPurchaseReplicaTest {

    private static final Long ID = 10L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 4, 12, 0);

    @Test
    @DisplayName("더 큰 번호의 상태만 덮어쓰고, 늦게 온 · 중복된 이벤트는 버린다 (릴레이는 순서를 보장하지 않음)")
    void applyOnlyNewerRevision() {
        IndividualGroupPurchaseReplica replica = open(List.of(1L), 0);

        assertThat(replica.applyOpenState(NOW.plusDays(2), List.of(1L, 2L, 3L), 2)).isTrue();
        assertThat(replica.applyOpenState(NOW.plusDays(1), List.of(1L, 2L), 1)).isFalse();
        assertThat(replica.applyOpenState(NOW.plusDays(1), List.of(9L), 2)).isFalse();

        assertThat(replica.getRevision()).isEqualTo(2);
        assertThat(replica.getDeadlineAt()).isEqualTo(NOW.plusDays(2));
        assertThat(replica.getProductIds()).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    @DisplayName("종료는 끝 상태라 번호가 작아도 닫고, 그 뒤에 온 열림 · 갱신은 다시 열지 못한다")
    void closedIsTerminal() {
        IndividualGroupPurchaseReplica replica = open(List.of(1L), 5);

        assertThat(replica.applyClosed(NOW.plusDays(1), NOW, 3)).isTrue();
        assertThat(replica.applyClosed(NOW.plusDays(1), NOW.plusMinutes(1), 9)).isFalse();
        assertThat(replica.applyOpenState(NOW.plusDays(3), List.of(1L, 2L), 8)).isFalse();

        assertThat(replica.isClosed()).isTrue();
        assertThat(replica.getClosedAt()).isEqualTo(NOW);
        assertThat(replica.getRevision()).isEqualTo(5);
        assertThat(replica.isOpen(NOW.minusHours(1))).isFalse();
    }

    @Test
    @DisplayName("주문 생성 판단: 닫혔거나 마감 시각이 지났으면 판매 마감, 팔지 않는 상품이 섞이면 거절")
    void validateOrderable() {
        IndividualGroupPurchaseReplica replica = open(List.of(1L, 2L), 0);

        assertThatNoException().isThrownBy(() -> replica.validateOrderable(List.of(1L, 2L), NOW));
        assertThatThrownBy(() -> replica.validateOrderable(List.of(1L, 3L), NOW))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_GROUP_PURCHASE_PRODUCT_NOT_SOLD.getMsg());
        assertThatThrownBy(() -> replica.validateOrderable(List.of(1L), NOW.plusDays(1)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_GROUP_PURCHASE_SALE_CLOSED.getMsg());

        replica.applyClosed(NOW.plusDays(1), NOW.minusMinutes(1), 1);
        assertThatThrownBy(() -> replica.validateOrderable(List.of(1L), NOW))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_GROUP_PURCHASE_SALE_CLOSED.getMsg());
    }

    @Test
    @DisplayName("결제 마감 = 판매 마감: 닫혔거나 마감 시각이 지났으면 언제 만든 주문이든 결제 불가, 열려 있어도 빠진 상품이면 결제 불가")
    void canAcceptPayment() {
        IndividualGroupPurchaseReplica replica = open(List.of(1L, 2L), 0);
        assertThat(replica.canAcceptPayment(List.of(1L, 2L), NOW)).isTrue();
        assertThat(replica.canAcceptPayment(List.of(3L), NOW)).isFalse();
        assertThat(replica.canAcceptPayment(List.of(1L), NOW.plusDays(1))).isFalse();

        replica.applyClosed(NOW.plusDays(1), NOW, 3);
        assertThat(replica.canAcceptPayment(List.of(1L), NOW.plusSeconds(1))).isFalse();

        IndividualGroupPurchaseReplica closedFirst = IndividualGroupPurchaseReplica.of(
                ID, NOW.plusDays(1), true, NOW, List.of(), 4);
        assertThat(closedFirst.canAcceptPayment(List.of(1L), NOW.minusMinutes(1))).isFalse();
    }

    private static IndividualGroupPurchaseReplica open(List<Long> productIds, long revision) {
        return IndividualGroupPurchaseReplica.of(ID, NOW.plusDays(1), false, null, productIds, revision);
    }

    @Test
    @DisplayName("페이백 조건은 비어 있을 때만 채우고, 닫힌 사본에도 채울 수 있다 (종료가 열림보다 먼저 와도 빠지지 않게)")
    void fillPaybackCondition() {
        IndividualGroupPurchaseReplica replica = IndividualGroupPurchaseReplica.of(
                1L, LocalDateTime.of(2026, 11, 10, 12, 0), true, LocalDateTime.of(2026, 11, 9, 12, 0), List.of(), 3);
        assertThat(replica.hasPaybackCondition()).isFalse();

        replica.fillPaybackCondition(null, null);
        assertThat(replica.hasPaybackCondition()).isFalse();

        replica.fillPaybackCondition(10, 1_000L);
        replica.fillPaybackCondition(20, 2_000L);

        assertThat(replica.hasPaybackCondition()).isTrue();
        assertThat(replica.getPaybackMinBuyers()).isEqualTo(10);
        assertThat(replica.getPaybackAmount()).isEqualTo(1_000L);
    }
}
