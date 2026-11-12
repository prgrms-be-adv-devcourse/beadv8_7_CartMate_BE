package com.cartmate.cartmate.boundedContext.orderService.order.domain.groupshared;

import static com.cartmate.cartmate.boundedContext.orderService.order.support.OrderFixtures.sharedLine;
import static com.cartmate.cartmate.boundedContext.orderService.order.support.OrderFixtures.address;
import com.cartmate.cartmate.shared.delivery.DeliveryCancelReason;
import com.cartmate.cartmate.shared.order.SharedGroupOrderCancelReason;
import com.cartmate.cartmate.shared.order.SharedGroupOrderStatus;
import com.cartmate.cartmate.boundedContext.orderService.order.exception.OrderErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static com.cartmate.cartmate.boundedContext.orderService.order.support.OrderFixtures.sharedOrder;
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
        SharedGroupOrder order = sharedOrder(CREATOR, MEMBER, CLOSED_AT);

        boolean canceled = order.cancelByCreator(CREATOR, NOW);

        assertThat(canceled).isTrue();
        assertThat(order.getStatus()).isEqualTo(SharedGroupOrderStatus.CANCELED);
        assertThat(order.getCancelReason()).isEqualTo(SharedGroupOrderCancelReason.CREATOR_CANCELED);
        assertThat(order.getCanceledAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("생성자가 아니면 공동 주문을 취소할 수 없다")
    void cancelByNonCreator() {
        SharedGroupOrder order = sharedOrder(CREATOR, MEMBER, CLOSED_AT);

        assertThatThrownBy(() -> order.cancelByCreator(MEMBER, NOW))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_CREATOR_ONLY.getMsg());
        assertThat(order.getStatus()).isEqualTo(SharedGroupOrderStatus.PAYING);
    }

    @Test
    @DisplayName("결제 중이 아닌 공동 주문은 취소할 수 없다")
    void cancelNotPaying() {
        SharedGroupOrder order = sharedOrder(CREATOR, MEMBER, CLOSED_AT);
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
        SharedGroupOrder order = sharedOrder(CREATOR, MEMBER, CLOSED_AT);
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
        SharedGroupOrder order = sharedOrder(CREATOR, MEMBER, CLOSED_AT);
        order.cancelByCreator(CREATOR, NOW);
        LocalDateTime graceEnd = order.getPaymentDeadlineAt().plus(SharedGroupOrder.GRACE_PERIOD);

        boolean canceled = order.cancelByDeadline(graceEnd);

        assertThat(canceled).isFalse();
        assertThat(order.getCancelReason()).isEqualTo(SharedGroupOrderCancelReason.CREATOR_CANCELED);
    }

    @Test
    @DisplayName("재고가 부족하면 결제 중인 공동 주문을 취소한다")
    void cancelByOutOfStock() {
        SharedGroupOrder order = sharedOrder(CREATOR, MEMBER, CLOSED_AT);

        boolean canceled = order.cancelByOutOfStock(NOW);

        assertThat(canceled).isTrue();
        assertThat(order.getStatus()).isEqualTo(SharedGroupOrderStatus.CANCELED);
        assertThat(order.getCancelReason()).isEqualTo(SharedGroupOrderCancelReason.OUT_OF_STOCK);
        assertThat(order.getCanceledAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("확정된 공동 주문에서 한 판매자 몫만 취소하면 그 항목만 사유와 함께 취소되고 주문은 확정으로 남는다")
    void cancelSellerItemsKeepsOrderConfirmed() {
        SharedGroupOrder order = confirmedTwoSellers();

        long amount = order.cancelSellerItems(7L, DeliveryCancelReason.SELLER_OUT_OF_STOCK, NOW);

        assertThat(amount).isEqualTo(10_000L);
        assertThat(order.getStatus()).isEqualTo(SharedGroupOrderStatus.CONFIRMED);
        assertThat(order.getItems()).extracting(SharedGroupOrderItem::getCancelReason)
                .containsExactly(DeliveryCancelReason.SELLER_OUT_OF_STOCK, null);
        assertThat(order.canceledAmountsBySeller()).containsExactly(java.util.Map.entry(7L, 10_000L));
    }

    @Test
    @DisplayName("마지막 판매자 몫까지 판매자 사유로 취소되면 공동 주문도 판매자 사유 취소로 취소되고, 다시 오면 0원")
    void cancelSellerItemsCancelsOrderWhenAllItemsCanceled() {
        SharedGroupOrder order = confirmedTwoSellers();
        order.cancelSellerItems(7L, DeliveryCancelReason.SALES_STOPPED, NOW);

        assertThat(order.cancelSellerItems(8L, DeliveryCancelReason.SELLER_PRODUCT_ISSUE, NOW.plusMinutes(1)))
                .isEqualTo(10_000L);

        assertThat(order.getStatus()).isEqualTo(SharedGroupOrderStatus.CANCELED);
        assertThat(order.getCancelReason()).isEqualTo(SharedGroupOrderCancelReason.SELLER_CANCELED);
        assertThat(order.getCanceledAt()).isEqualTo(NOW.plusMinutes(1));
        assertThat(order.cancelSellerItems(8L, DeliveryCancelReason.SELLER_PRODUCT_ISSUE, NOW.plusMinutes(2))).isZero();
    }

    @Test
    @DisplayName("마지막 판매자 몫이 판매 중지로 취소되면 공동 주문은 판매 중지로 취소된다")
    void cancelSellerItemsBySalesStopCancelsOrderWithSalesStopped() {
        SharedGroupOrder order = confirmedTwoSellers();
        order.cancelSellerItems(7L, DeliveryCancelReason.SELLER_OTHER, NOW);

        order.cancelSellerItems(8L, DeliveryCancelReason.SALES_STOPPED, NOW.plusMinutes(1));

        assertThat(order.getCancelReason()).isEqualTo(SharedGroupOrderCancelReason.SALES_STOPPED);
    }

    @Test
    @DisplayName("결제 중인 공동 주문은 판매자 몫을 취소하지 않는다")
    void cancelSellerItemsIgnoresPayingOrder() {
        SharedGroupOrder order = sharedOrder(10L, CREATOR, MEMBER, CLOSED_AT,
                sharedLine(2L, 4L, 7L, 1, 10_000L));

        assertThat(order.cancelSellerItems(7L, DeliveryCancelReason.SELLER_OTHER, NOW)).isZero();
        assertThat(order.getStatus()).isEqualTo(SharedGroupOrderStatus.PAYING);
        assertThat(order.getItems()).noneMatch(SharedGroupOrderItem::isCanceled);
    }

    private static SharedGroupOrder confirmedTwoSellers() {
        SharedGroupOrder order = sharedOrder(10L, CREATOR, MEMBER, CLOSED_AT,
                sharedLine(2L, 4L, 7L, 1, 10_000L), sharedLine(3L, 5L, 8L, 2, 5_000L));
        order.markStockReserved();
        order.pay(CREATOR, 1L, CLOSED_AT, address());
        order.pay(MEMBER, 2L, CLOSED_AT, null);
        return order;
    }
}
