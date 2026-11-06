package com.cartmate.cartmate.boundedContext.orderService.order.domain.personal;

import com.cartmate.cartmate.shared.order.OrderCancelReason;
import com.cartmate.cartmate.shared.order.OrderStatus;
import com.cartmate.cartmate.shared.order.OrderType;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.DeliveryAddress;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.Order.OrderLine;
import com.cartmate.cartmate.boundedContext.orderService.order.exception.OrderErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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
    @DisplayName("판매 중지: 한 판매자의 상품이 여러 개면 그 금액을 모두 합쳐 환불 금액으로 돌려주고, 다른 판매자 상품은 남는다")
    void cancelSellerItemsRefundsSellerTotalAndKeepsOtherSellerItems() {
        Order order = Order.createPersonal("ORD-3", BUYER, List.of(
                new OrderLine(10L, 11L, 7L, "유니폼", null, 30_000L, 1),
                new OrderLine(20L, 21L, 7L, "양말", null, 2_500L, 2),
                new OrderLine(30L, 31L, 8L, "축구공", null, 20_000L, 1)
        ), address(), NOW);
        order.markPaid(99L, NOW.plusMinutes(1));

        long refund = order.cancelSellerItemsBySalesStopped(7L, NOW.plusMinutes(2));

        assertThat(refund).isEqualTo(30_000L + 2_500L * 2);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getItems()).filteredOn(OrderItem::isCanceled).hasSize(2);
    }

    @Test
    @DisplayName("판매 중지: 모든 상품이 취소되면 주문도 판매 중지로 취소되고, 같은 판매자를 다시 취소하면 0원")
    void cancelSellerItemsCancelsOrderWhenAllItemsCanceled() {
        Order order = Order.createPersonal("ORD-4", BUYER, List.of(
                new OrderLine(10L, 11L, 7L, "유니폼", null, 30_000L, 1),
                new OrderLine(20L, 21L, 7L, "양말", null, 2_500L, 2)
        ), address(), NOW);
        order.markPaid(99L, NOW.plusMinutes(1));

        assertThat(order.cancelSellerItemsBySalesStopped(7L, NOW.plusMinutes(2))).isEqualTo(35_000L);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(order.getCancelReason()).isEqualTo(OrderCancelReason.SALES_STOPPED);
        assertThat(order.cancelSellerItemsBySalesStopped(7L, NOW.plusMinutes(3))).isZero();
    }

    @Test
    @DisplayName("판매 중지: 결제 전 주문이나 판매자 상품이 없으면 0원 (판매자 없는 상품이 섞여 있어도 오류 없이)")
    void cancelSellerItemsReturnsZero() {
        Order pending = personalOrder();
        assertThat(pending.cancelSellerItemsBySalesStopped(12L, NOW)).isZero();

        Order paid = personalOrder(); // 상품B는 판매자가 비어 있다
        paid.markPaid(99L, NOW.plusMinutes(1));
        assertThat(paid.cancelSellerItemsBySalesStopped(999L, NOW.plusMinutes(2))).isZero();
        assertThat(paid.getStatus()).isEqualTo(OrderStatus.PAID);
    }


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

    @ParameterizedTest(name = "{0} 상태의 주문")
    @EnumSource(value = OrderStatus.class, names = {"PAYMENT_PENDING", "PAID"})
    @DisplayName("구매자는 결제 대기 · 결제 완료된 주문을 취소할 수 있다")
    void cancelByBuyer(OrderStatus status) {
        Order order = personalOrder();
        if (status == OrderStatus.PAID) {
            order.markPaid(1L, NOW);
        }
        LocalDateTime canceledAt = NOW.plusMinutes(1);

        boolean canceled = order.cancelByBuyer(BUYER, canceledAt);

        assertThat(canceled).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(order.getCancelReason()).isEqualTo(OrderCancelReason.BUYER_CANCELED);
        assertThat(order.getCanceledAt()).isEqualTo(canceledAt);
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
    @DisplayName("결제 기한 1초 전에는 기한 만료로 취소되지 않고, 기한이 되면 취소된다")
    void cancelByDeadlineBoundary() {
        Order order = personalOrder();
        LocalDateTime deadline = order.getPaymentDeadlineAt();

        assertThat(order.cancelByDeadline(deadline.minusSeconds(1))).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);

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

    // ===== 쿠폰 할인 =====

    private Order twoSellerOrder() {
        // 판매자 7: 30,000 / 판매자 8: 20,000 → 50,000
        return Order.createPersonal("ORD-C", BUYER, List.of(
                new OrderLine(10L, 11L, 7L, "유니폼", null, 30_000L, 1),
                new OrderLine(30L, 31L, 8L, "축구공", null, 20_000L, 1)
        ), address(), NOW);
    }

    @Test
    @DisplayName("쿠폰: 할인을 기록하면 결제 금액이 줄고, 같은 쿠폰·같은 금액으로 다시 와도 그대로, 다른 쿠폰이면 거절")
    void applyCoupon() {
        Order order = twoSellerOrder();

        order.applyCoupon(5L, 10_000L, true, null);

        assertThat(order.payAmount()).isEqualTo(40_000L);
        assertThat(order.getDiscountBurden()).isEqualTo(OrderDiscountBurden.PLATFORM);
        order.applyCoupon(5L, 10_000L, true, null); // 재시도
        assertThat(order.payAmount()).isEqualTo(40_000L);
        assertThatThrownBy(() -> order.applyCoupon(6L, 3_000L, true, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_COUPON_ALREADY_APPLIED.getMsg());
    }

    @Test
    @DisplayName("쿠폰: 할인이 주문 금액보다 크면 거절")
    void applyCouponOverTotal() {
        Order order = twoSellerOrder();
        assertThatThrownBy(() -> order.applyCoupon(5L, 50_001L, true, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_INVALID_DISCOUNT.getMsg());
    }

    @Test
    @DisplayName("쿠폰: 쿠폰에 넘기는 금액 정보는 주문 금액과 판매자별 상품 금액")
    void couponTarget() {
        var target = twoSellerOrder().couponTarget();
        assertThat(target.totalAmount()).isEqualTo(50_000L);
        assertThat(target.amountOfSeller(7L)).isEqualTo(30_000L);
        assertThat(target.amountOfSeller(8L)).isEqualTo(20_000L);
    }

    @Test
    @DisplayName("부분 취소 + 전체 쿠폰: 취소 상품 몫의 할인을 비율로 빼고 환불, 마지막 취소는 남은 결제 금액 전부 (합계 = 낸 돈)")
    void cancelSellerItemsWithPlatformCoupon() {
        Order order = twoSellerOrder();
        order.applyCoupon(5L, 10_000L, true, null); // 50,000 → 40,000
        order.markPaid(99L, NOW.plusMinutes(1));

        long first = order.cancelSellerItemsBySalesStopped(8L, NOW.plusMinutes(2));  // 20,000 - 4,000
        long second = order.cancelSellerItemsBySalesStopped(7L, NOW.plusMinutes(3)); // 남은 24,000

        assertThat(first).isEqualTo(16_000L);
        assertThat(second).isEqualTo(24_000L);
        assertThat(first + second).isEqualTo(order.payAmount());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(order.couponRestorable()).isFalse(); // 앞서 부분 취소가 있었으니 쿠폰은 돌려주지 않음
    }

    @Test
    @DisplayName("부분 취소 + 판매자 쿠폰: 그 판매자 상품 취소에만 할인을 빼고, 다른 판매자 상품은 전액 환불")
    void cancelSellerItemsWithSellerCoupon() {
        Order order = twoSellerOrder();
        order.applyCoupon(5L, 3_000L, false, 7L); // 판매자 7 쿠폰: 50,000 → 47,000
        order.markPaid(99L, NOW.plusMinutes(1));

        assertThat(order.cancelSellerItemsBySalesStopped(8L, NOW.plusMinutes(2))).isEqualTo(20_000L);
        assertThat(order.cancelSellerItemsBySalesStopped(7L, NOW.plusMinutes(3))).isEqualTo(27_000L);
    }

    @Test
    @DisplayName("부분 취소 + 나눗셈 끝전: 각 취소는 원 미만 버림, 마지막에 남은 금액을 돌려줘 합계가 결제 금액과 같다")
    void cancelSellerItemsRemainder() {
        Order order = Order.createPersonal("ORD-R", BUYER, List.of(
                new OrderLine(10L, 11L, 7L, "A", null, 10_000L, 1),
                new OrderLine(20L, 21L, 8L, "B", null, 10_000L, 1),
                new OrderLine(30L, 31L, 9L, "C", null, 10_000L, 1)
        ), address(), NOW);
        order.applyCoupon(5L, 1_000L, true, null); // 30,000 → 29,000, 상품당 333.33원
        order.markPaid(99L, NOW.plusMinutes(1));

        long a = order.cancelSellerItemsBySalesStopped(7L, NOW.plusMinutes(2));
        long b = order.cancelSellerItemsBySalesStopped(8L, NOW.plusMinutes(3));
        long c = order.cancelSellerItemsBySalesStopped(9L, NOW.plusMinutes(4));

        assertThat(a).isEqualTo(9_667L);
        assertThat(b).isEqualTo(9_667L);
        assertThat(a + b + c).isEqualTo(29_000L);
    }

    @Test
    @DisplayName("쿠폰 복구: 한 번에 전부 취소되면 쿠폰을 돌려준다 (구매자 취소, 판매자 하나뿐인 주문의 판매 중지)")
    void couponRestorableWhenWholeOrderCanceledAtOnce() {
        Order byBuyer = twoSellerOrder();
        byBuyer.applyCoupon(5L, 10_000L, true, null);
        byBuyer.markPaid(99L, NOW.plusMinutes(1));
        byBuyer.cancelByBuyer(BUYER, NOW.plusMinutes(2));
        assertThat(byBuyer.couponRestorable()).isTrue();

        Order oneSeller = Order.createPersonal("ORD-S", BUYER, List.of(
                new OrderLine(10L, 11L, 7L, "유니폼", null, 30_000L, 1)), address(), NOW);
        oneSeller.applyCoupon(5L, 3_000L, true, null);
        oneSeller.markPaid(99L, NOW.plusMinutes(1));
        assertThat(oneSeller.cancelSellerItemsBySalesStopped(7L, NOW.plusMinutes(2))).isEqualTo(27_000L);
        assertThat(oneSeller.couponRestorable()).isTrue();
    }

    @Test
    @DisplayName("판매 중지로 판매자 항목을 취소하면 그 판매자 항목 금액을 모두 더해 돌려주고, 다른 판매자 항목은 남는다 (ISS-26)")
    void cancelSellerItemsSumsAllItemsOfSeller() {
        Order order = Order.createPersonal("ORD-3", BUYER, List.of(
                new OrderLine(10L, 11L, 12L, "상품A", null, 10_000L, 2),
                new OrderLine(30L, 31L, 12L, "상품C", null, 3_000L, 1),
                new OrderLine(20L, 21L, 99L, "상품B", null, 5_000L, 1)), address(), NOW);
        order.markPaid(1L, NOW);

        long amount = order.cancelSellerItemsBySalesStopped(12L, NOW);

        assertThat(amount).isEqualTo(23_000L);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.cancelSellerItemsBySalesStopped(12L, NOW)).isZero();
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
