package com.cartmate.cartmate.boundedContext.order.domain.personal;

import com.cartmate.cartmate.boundedContext.order.exception.OrderErrorCode;
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
    @DisplayName("결제 재검사: 닫히기 전 · 마감 전에 만든 주문만 '팔고 있었다'이고, 지금 목록에 없는 상품이 든 주문은 아니다")
    void wasSellingAt() {
        IndividualGroupPurchaseReplica replica = open(List.of(1L, 2L), 0);
        replica.applyClosed(NOW.plusDays(1), NOW, 3);

        assertThat(replica.wasSellingAt(List.of(1L), NOW.minusSeconds(1))).isTrue();
        assertThat(replica.wasSellingAt(List.of(1L), NOW)).isFalse();

        IndividualGroupPurchaseReplica openReplica = open(List.of(1L, 2L), 0);
        assertThat(openReplica.wasSellingAt(List.of(3L), NOW.minusMinutes(5))).isFalse();

        IndividualGroupPurchaseReplica memberPurchase = open(List.of(1L), 0);
        assertThat(memberPurchase.wasSellingAt(List.of(1L), NOW.plusDays(1).minusSeconds(1))).isTrue();
        assertThat(memberPurchase.wasSellingAt(List.of(1L), NOW.plusDays(1))).isFalse();
    }

    @Test
    @DisplayName("닫힌 사본은 상품 목록을 믿지 않는다: 종료가 먼저 와 빈 목록이거나 판매 중지로 목록이 비어도 종료 전 주문은 결제할 수 있다")
    void closedReplicaIgnoresProductList() {
        IndividualGroupPurchaseReplica closedFirst = IndividualGroupPurchaseReplica.of(
                ID, NOW.plusDays(1), true, NOW, List.of(), 4);

        assertThat(closedFirst.wasSellingAt(List.of(1L), NOW.minusMinutes(1))).isTrue();
        assertThat(closedFirst.wasSellingAt(List.of(1L), NOW.plusSeconds(1))).isFalse();
    }

    private static IndividualGroupPurchaseReplica open(List<Long> productIds, long revision) {
        return IndividualGroupPurchaseReplica.of(ID, NOW.plusDays(1), false, null, productIds, revision);
    }
}
