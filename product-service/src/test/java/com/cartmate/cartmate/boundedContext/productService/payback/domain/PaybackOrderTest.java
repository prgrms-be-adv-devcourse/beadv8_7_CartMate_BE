package com.cartmate.cartmate.boundedContext.productService.payback.domain;

import com.cartmate.cartmate.shared.order.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaybackOrderTest {

    @Test
    @DisplayName("상태는 결제 대기 → 결제 완료 → 취소 방향으로만 바뀌어 이벤트가 늦게 와도 되돌아가지 않는다")
    void statusOnlyMovesForward() {
        PaybackOrder order = new PaybackOrder(1L);

        order.applyCanceled();
        order.applyPlaced(5L, 10L, List.of(PaybackOrderItem.of(1L, 100L, 7L, 2)), OrderStatus.PAID);
        order.applyPlaced(5L, 10L, List.of(), OrderStatus.PAYMENT_PENDING);

        assertThat(order.isCanceled()).isTrue();
        assertThat(order.getIndividualGroupPurchaseId()).isEqualTo(5L);
        assertThat(order.getItems()).hasSize(1);
    }

    @Test
    @DisplayName("결제 대기 뒤 결제 완료가 오면 결제 완료가 되고, 같은 이벤트가 다시 와도 항목이 늘지 않는다")
    void placedIsIdempotent() {
        PaybackOrder order = new PaybackOrder(1L);
        List<PaybackOrderItem> items = List.of(PaybackOrderItem.of(1L, 100L, 7L, 2));

        order.applyPlaced(5L, 10L, items, OrderStatus.PAYMENT_PENDING);
        order.applyPlaced(5L, 10L, items, OrderStatus.PAID);
        order.applyPlaced(5L, 10L, items, OrderStatus.PAID);

        assertThat(order.isPaid()).isTrue();
        assertThat(order.getItems()).hasSize(1);
    }

    @Test
    @DisplayName("판매자 취소 · 구매확정은 주문을 모를 때 먼저 와도 기록해 둔다")
    void sellerFactsBeforePlaced() {
        PaybackOrder order = new PaybackOrder(1L);

        order.applyConfirmed(7L);
        order.applySellerItemsCanceled(8L);
        order.applyPlaced(5L, 10L, List.of(PaybackOrderItem.of(1L, 100L, 7L, 2)), OrderStatus.PAID);

        assertThat(order.isSellerConfirmed(7L)).isTrue();
        assertThat(order.isSellerCanceled(8L)).isTrue();
    }
}
