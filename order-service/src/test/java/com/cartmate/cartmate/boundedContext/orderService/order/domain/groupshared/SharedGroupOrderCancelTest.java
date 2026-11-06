package com.cartmate.cartmate.boundedContext.orderService.order.domain.groupshared;

import com.cartmate.cartmate.shared.order.SharedGroupOrderCancelReason;
import com.cartmate.cartmate.shared.order.SharedGroupOrderStatus;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.groupshared.SharedGroupOrder.OrderItemLine;
import com.cartmate.cartmate.boundedContext.orderService.order.exception.OrderErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SharedGroupOrderCancelTest {

    private static final Long CREATOR = 1L;
    private static final Long MEMBER = 2L;
    private static final LocalDateTime CLOSED_AT = LocalDateTime.of(2026, 10, 19, 12, 0);
    private static final LocalDateTime NOW = CLOSED_AT.plusHours(1);

    @Test
    @DisplayName("생성자는 결제 중인 공동 주문을 취소할 수 있다")
    void cancelByCreator() {
        SharedGroupOrder order = order();

        boolean canceled = order.cancelByCreator(CREATOR, NOW);

        assertThat(canceled).isTrue();
        assertThat(order.getStatus()).isEqualTo(SharedGroupOrderStatus.CANCELED);
        assertThat(order.getCancelReason()).isEqualTo(SharedGroupOrderCancelReason.CREATOR_CANCELED);
        assertThat(order.getCanceledAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("생성자가 아니면 공동 주문을 취소할 수 없다")
    void cancelByNonCreator() {
        SharedGroupOrder order = order();

        assertThatThrownBy(() -> order.cancelByCreator(MEMBER, NOW))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_CREATOR_ONLY.getMsg());
        assertThat(order.getStatus()).isEqualTo(SharedGroupOrderStatus.PAYING);
    }

    @Test
    @DisplayName("결제 중이 아닌 공동 주문은 취소할 수 없다")
    void cancelNotPaying() {
        SharedGroupOrder order = order();
        order.cancelByCreator(CREATOR, NOW);

        assertThatThrownBy(() -> order.cancelByCreator(CREATOR, NOW))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_NOT_PAYING.getMsg());
        assertThatThrownBy(() -> order.cancelByOutOfStock(NOW))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_NOT_PAYING.getMsg());
    }

    @Test
    @DisplayName("결제 기한에 유예 시간을 더한 시각 1초 전에는 기한 만료로 취소하지 않고, 그 시각이 되면 취소한다")
    void cancelByDeadlineGraceBoundary() {
        SharedGroupOrder order = order();
        LocalDateTime graceEnd = order.getPaymentDeadlineAt().plus(SharedGroupOrder.GRACE_PERIOD);

        assertThat(order.cancelByDeadline(graceEnd.minusSeconds(1))).isFalse();
        assertThat(order.getStatus()).isEqualTo(SharedGroupOrderStatus.PAYING);
        assertThat(order.getCancelReason()).isNull();

        boolean canceled = order.cancelByDeadline(graceEnd);

        assertThat(canceled).isTrue();
        assertThat(order.getStatus()).isEqualTo(SharedGroupOrderStatus.CANCELED);
        assertThat(order.getCancelReason()).isEqualTo(SharedGroupOrderCancelReason.DEADLINE_EXPIRED);
        assertThat(order.getCanceledAt()).isEqualTo(graceEnd);
    }

    @Test
    @DisplayName("이미 취소된 공동 주문은 기한 만료로 다시 취소하지 않는다")
    void cancelByDeadlineAlreadyCanceled() {
        SharedGroupOrder order = order();
        order.cancelByCreator(CREATOR, NOW);
        LocalDateTime graceEnd = order.getPaymentDeadlineAt().plus(SharedGroupOrder.GRACE_PERIOD);

        boolean canceled = order.cancelByDeadline(graceEnd);

        assertThat(canceled).isFalse();
        assertThat(order.getCancelReason()).isEqualTo(SharedGroupOrderCancelReason.CREATOR_CANCELED);
    }

    @Test
    @DisplayName("재고가 부족하면 결제 중인 공동 주문을 취소한다")
    void cancelByOutOfStock() {
        SharedGroupOrder order = order();

        boolean canceled = order.cancelByOutOfStock(NOW);

        assertThat(canceled).isTrue();
        assertThat(order.getStatus()).isEqualTo(SharedGroupOrderStatus.CANCELED);
        assertThat(order.getCancelReason()).isEqualTo(SharedGroupOrderCancelReason.OUT_OF_STOCK);
        assertThat(order.getCanceledAt()).isEqualTo(NOW);
    }

    private SharedGroupOrder order() {
        return SharedGroupOrder.create(10L, CREATOR, List.of(CREATOR, MEMBER),
                List.of(new OrderItemLine(2L, 4L, null, "상품", null, 2, 25000L)), CLOSED_AT);
    }
}
