package com.cartmate.cartmate.boundedContext.order.domain.personal;

import com.cartmate.cartmate.boundedContext.order.domain.DeliveryAddress;
import com.cartmate.cartmate.boundedContext.order.domain.personal.Order.OrderLine;
import com.cartmate.cartmate.boundedContext.order.exception.OrderErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private static final Long BUYER = 1L;
    private static final Long OTHER = 2L;
    private static final Long INDIVIDUAL_GROUP_PURCHASE_ID = 100L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 12, 0);

    @Test
    @DisplayName("개인 주문은 결제 대기 상태로 생성되고 총액은 단가 x 수량의 합이다")
    void createPersonal() {
        Order order = personalOrder();

        assertThat(order.getOrderType()).isEqualTo(OrderType.PERSONAL);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(order.getTotalAmount()).isEqualTo(10_000L * 2 + 5_000L);
        assertThat(order.getItems()).hasSize(2);
        assertThat(order.getIndividualGroupPurchaseId()).isNull();
        assertThat(order.isIndividualGroup()).isFalse();
        assertThat(order.isBuyer(BUYER)).isTrue();
        assertThat(order.getPaymentDeadlineAt()).isEqualTo(NOW.plusMinutes(30));
    }

    @Test
    @DisplayName("공동구매 개인 주문의 결제 기한은 판매 마감이 더 늦으면 30분 뒤다")
    void createIndividualGroupWithLateSaleDeadline() {
        Order order = individualGroupOrder(NOW.plusHours(2));

        assertThat(order.getOrderType()).isEqualTo(OrderType.INDIVIDUAL_GROUP);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(order.getIndividualGroupPurchaseId()).isEqualTo(INDIVIDUAL_GROUP_PURCHASE_ID);
        assertThat(order.isIndividualGroup()).isTrue();
        assertThat(order.getPaymentDeadlineAt()).isEqualTo(NOW.plusMinutes(30));
    }

    @Test
    @DisplayName("공동구매 개인 주문의 결제 기한은 판매 마감이 더 이르면 판매 마감이다")
    void createIndividualGroupWithEarlySaleDeadline() {
        LocalDateTime saleDeadlineAt = NOW.plusMinutes(10);

        Order order = individualGroupOrder(saleDeadlineAt);

        assertThat(order.getPaymentDeadlineAt()).isEqualTo(saleDeadlineAt);
    }

    @Test
    @DisplayName("주문 상품이 없으면 주문을 생성할 수 없다")
    void createWithoutLines() {
        assertThatThrownBy(() -> Order.createPersonal("ORD-1", BUYER, List.of(), address(), NOW))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_NO_ITEMS.getMsg());
        assertThatThrownBy(() -> Order.createPersonal("ORD-1", BUYER, null, address(), NOW))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_NO_ITEMS.getMsg());
    }

    @Test
    @DisplayName("수량이 1 미만이거나 가격이 음수면 주문을 생성할 수 없다")
    void createWithInvalidLine() {
        assertThatThrownBy(() -> Order.createPersonal("ORD-1", BUYER, List.of(line(10_000L, 0)), address(), NOW))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_INVALID_QUANTITY.getMsg());
        assertThatThrownBy(() -> Order.createPersonal("ORD-1", BUYER, List.of(line(-1L, 1)), address(), NOW))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_INVALID_PRICE.getMsg());
    }

    @Test
    @DisplayName("배송지가 없으면 주문을 생성할 수 없다")
    void createWithoutAddress() {
        assertThatThrownBy(() -> Order.createPersonal("ORD-1", BUYER, lines(), null, NOW))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_INVALID_DELIVERY_ADDRESS.getMsg());
    }

    @Test
    @DisplayName("결제 기한 안에 결제하면 결제 완료 상태가 된다")
    void markPaid() {
        Order order = personalOrder();
        LocalDateTime paidAt = order.getPaymentDeadlineAt();

        order.markPaid(1L, paidAt);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getPaidAt()).isEqualTo(paidAt);
    }

    @Test
    @DisplayName("결제 기한이 지나면 결제할 수 없다")
    void markPaidAfterDeadline() {
        Order order = personalOrder();
        LocalDateTime paidAt = order.getPaymentDeadlineAt().plusSeconds(1);

        assertThat(order.canAcceptPayment(paidAt)).isFalse();
        assertThatThrownBy(() -> order.markPaid(1L, paidAt))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_PAYMENT_EXPIRED.getMsg());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
    }

    @Test
    @DisplayName("이미 결제한 주문은 다시 결제할 수 없다")
    void markPaidTwice() {
        Order order = personalOrder();
        order.markPaid(1L, NOW);

        assertThatThrownBy(() -> order.markPaid(2L, NOW))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_NOT_PAYMENT_PENDING.getMsg());
    }

    @Test
    @DisplayName("같은 결제 ID로 다시 결제 통보가 오면 상태를 바꾸지 않고 새로 반영하지 않았다고 알린다")
    void markPaidSamePaymentIdTwice() {
        Order order = personalOrder();

        assertThat(order.markPaid(1L, NOW)).isTrue();
        assertThat(order.markPaid(1L, NOW.plusMinutes(1))).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getPaymentId()).isEqualTo(1L);
        assertThat(order.getPaidAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("구매자는 결제 대기 중인 주문을 취소할 수 있다")
    void cancelByBuyerFromPending() {
        Order order = personalOrder();

        boolean canceled = order.cancelByBuyer(BUYER, NOW);

        assertThat(canceled).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(order.getCancelReason()).isEqualTo(OrderCancelReason.BUYER_CANCELED);
        assertThat(order.getCanceledAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("구매자는 결제 완료된 주문을 취소할 수 있다")
    void cancelByBuyerFromPaid() {
        Order order = personalOrder();
        order.markPaid(1L, NOW);

        boolean canceled = order.cancelByBuyer(BUYER, NOW.plusMinutes(1));

        assertThat(canceled).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(order.getCancelReason()).isEqualTo(OrderCancelReason.BUYER_CANCELED);
    }

    @Test
    @DisplayName("구매자가 아니면 주문을 취소할 수 없다")
    void cancelByOtherMember() {
        Order order = personalOrder();

        assertThatThrownBy(() -> order.cancelByBuyer(OTHER, NOW))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_BUYER_ONLY.getMsg());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
    }

    @Test
    @DisplayName("이미 취소된 주문은 다시 취소할 수 없다")
    void cancelAlreadyCanceled() {
        Order order = personalOrder();
        order.cancelByBuyer(BUYER, NOW);

        assertThatThrownBy(() -> order.cancelByBuyer(BUYER, NOW))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_ALREADY_CANCELED.getMsg());
    }

    @Test
    @DisplayName("결제 기한 전에는 기한 만료로 취소되지 않는다")
    void cancelByDeadlineBeforeDeadline() {
        Order order = personalOrder();

        boolean canceled = order.cancelByDeadline(order.getPaymentDeadlineAt().minusSeconds(1));

        assertThat(canceled).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
    }

    @Test
    @DisplayName("결제 기한이 되면 기한 만료로 취소된다")
    void cancelByDeadlineAtDeadline() {
        Order order = personalOrder();
        LocalDateTime deadline = order.getPaymentDeadlineAt();

        boolean canceled = order.cancelByDeadline(deadline);

        assertThat(canceled).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(order.getCancelReason()).isEqualTo(OrderCancelReason.PAYMENT_EXPIRED);
        assertThat(order.getCanceledAt()).isEqualTo(deadline);
    }

    @Test
    @DisplayName("결제 완료된 주문은 기한 만료로 취소되지 않는다")
    void cancelByDeadlineWhenPaid() {
        Order order = personalOrder();
        order.markPaid(1L, NOW);

        assertThat(order.cancelByDeadline(order.getPaymentDeadlineAt().plusHours(1))).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("결제 대기 중인 주문은 재고 부족으로 취소된다")
    void cancelByOutOfStock() {
        Order order = personalOrder();

        boolean canceled = order.cancelByOutOfStock(NOW);

        assertThat(canceled).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(order.getCancelReason()).isEqualTo(OrderCancelReason.OUT_OF_STOCK);
    }

    @Test
    @DisplayName("결제 대기 중이 아니면 재고 부족으로 취소할 수 없다")
    void cancelByOutOfStockWhenNotPending() {
        Order order = personalOrder();
        order.markPaid(1L, NOW);

        assertThatThrownBy(() -> order.cancelByOutOfStock(NOW))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_NOT_PAYMENT_PENDING.getMsg());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    private Order personalOrder() {
        return Order.createPersonal("ORD-1", BUYER, lines(), address(), NOW);
    }

    private Order individualGroupOrder(LocalDateTime saleDeadlineAt) {
        return Order.createIndividualGroup("ORD-2", BUYER, INDIVIDUAL_GROUP_PURCHASE_ID, saleDeadlineAt,
                lines(), address(), NOW);
    }

    private List<OrderLine> lines() {
        return List.of(
                new OrderLine(10L, 11L, 12L, "상품A", "빨강", 10_000L, 2),
                new OrderLine(20L, 21L, null, "상품B", null, 5_000L, 1)
        );
    }

    private OrderLine line(long unitPrice, int quantity) {
        return new OrderLine(10L, 11L, 12L, "상품A", null, unitPrice, quantity);
    }

    private DeliveryAddress address() {
        return DeliveryAddress.of("홍길동", "010-1234-5678", "12345", "서울시 강남구", null);
    }
}
