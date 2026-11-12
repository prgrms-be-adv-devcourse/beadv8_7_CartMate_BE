package com.cartmate.cartmate.boundedContext.orderService.order.domain.personal;

import com.cartmate.cartmate.shared.delivery.DeliveryCancelReason;
import com.cartmate.cartmate.shared.order.OrderCancelReason;
import com.cartmate.cartmate.shared.order.OrderStatus;
import com.cartmate.cartmate.shared.order.OrderType;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.Order.OrderLine;
import com.cartmate.cartmate.boundedContext.orderService.order.exception.OrderErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static com.cartmate.cartmate.boundedContext.orderService.order.support.OrderFixtures.addressWithoutDetail;
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
        ), addressWithoutDetail(), NOW);
        order.markPaid(99L, NOW.plusMinutes(1));

        long refund = order.cancelSellerItems(7L, DeliveryCancelReason.SALES_STOPPED, NOW.plusMinutes(2));

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
        ), addressWithoutDetail(), NOW);
        order.markPaid(99L, NOW.plusMinutes(1));

        assertThat(order.cancelSellerItems(7L, DeliveryCancelReason.SALES_STOPPED, NOW.plusMinutes(2))).isEqualTo(35_000L);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(order.getCancelReason()).isEqualTo(OrderCancelReason.SALES_STOPPED);
        assertThat(order.cancelSellerItems(7L, DeliveryCancelReason.SALES_STOPPED, NOW.plusMinutes(3))).isZero();
    }

    @Test
    @DisplayName("판매 중지: 결제 전 주문이나 판매자 상품이 없으면 0원 (판매자 없는 상품이 섞여 있어도 오류 없이)")
    void cancelSellerItemsReturnsZero() {
        Order pending = personalOrder();
        assertThat(pending.cancelSellerItems(12L, DeliveryCancelReason.SALES_STOPPED, NOW)).isZero();

        Order paid = personalOrder();
        paid.markPaid(99L, NOW.plusMinutes(1));
        assertThat(paid.cancelSellerItems(999L, DeliveryCancelReason.SALES_STOPPED, NOW.plusMinutes(2))).isZero();
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
        assertThatThrownBy(() -> Order.createPersonal("ORD-1", BUYER, List.of(), addressWithoutDetail(), NOW))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_NO_ITEMS.getMsg());
        assertThatThrownBy(() -> Order.createPersonal("ORD-1", BUYER, null, addressWithoutDetail(), NOW))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_NO_ITEMS.getMsg());
    }

    @Test
    @DisplayName("수량이 1 미만이거나 가격이 음수면 주문을 생성할 수 없다")
    void createWithInvalidLine() {
        assertThatThrownBy(() -> Order.createPersonal("ORD-1", BUYER, List.of(line(10_000L, 0)), addressWithoutDetail(), NOW))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_INVALID_QUANTITY.getMsg());
        assertThatThrownBy(() -> Order.createPersonal("ORD-1", BUYER, List.of(line(-1L, 1)), addressWithoutDetail(), NOW))
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
    @DisplayName("구매자가 결제 대기 주문을 취소하면 바로 취소된다")
    void cancelPendingByBuyer() {
        Order order = personalOrder();
        LocalDateTime canceledAt = NOW.plusMinutes(1);

        BuyerCancelResult result = order.cancelByBuyer(BUYER, canceledAt);

        assertThat(result).isEqualTo(BuyerCancelResult.CANCELED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(order.getCancelReason()).isEqualTo(OrderCancelReason.BUYER_CANCELED);
        assertThat(order.getCanceledAt()).isEqualTo(canceledAt);
    }

    @Test
    @DisplayName("구매자가 결제 완료 주문을 취소하면 취소 처리 중이 되고 요청 번호와 요청 시각이 남는다")
    void requestCancelOfPaidOrder() {
        Order order = personalOrder();
        order.markPaid(1L, NOW);
        LocalDateTime requestedAt = NOW.plusMinutes(1);

        BuyerCancelResult result = order.cancelByBuyer(BUYER, requestedAt);

        assertThat(result).isEqualTo(BuyerCancelResult.REQUESTED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCEL_REQUESTED);
        assertThat(order.getCancelRequestSeq()).isEqualTo(1);
        assertThat(order.getCancelRequestedAt()).isEqualTo(requestedAt);
        assertThat(order.getCancelLastSentAt()).isEqualTo(requestedAt);
        assertThat(order.isPaid()).isTrue();
    }

    @Test
    @DisplayName("취소 처리 중에 다시 요청하면 번호와 처음 요청 시각은 그대로 두고 발송 시각만 갱신한다")
    void resendWhileCancelRequested() {
        Order order = personalOrder();
        order.markPaid(1L, NOW);
        order.cancelByBuyer(BUYER, NOW.plusMinutes(1));

        BuyerCancelResult result = order.cancelByBuyer(BUYER, NOW.plusMinutes(5));

        assertThat(result).isEqualTo(BuyerCancelResult.RESENT);
        assertThat(order.getCancelRequestSeq()).isEqualTo(1);
        assertThat(order.getCancelRequestedAt()).isEqualTo(NOW.plusMinutes(1));
        assertThat(order.getCancelLastSentAt()).isEqualTo(NOW.plusMinutes(5));
    }

    @Test
    @DisplayName("재발행은 마지막 발송 뒤 정한 시간이 지난 취소 처리 중 주문만 한다")
    void resendCancelRequestAfterInterval() {
        Order order = personalOrder();
        order.markPaid(1L, NOW);
        order.cancelByBuyer(BUYER, NOW);
        Duration resendAfter = Duration.ofMinutes(10);

        assertThat(order.resendCancelRequest(NOW.plusMinutes(9), resendAfter)).isFalse();
        assertThat(order.resendCancelRequest(NOW.plusMinutes(10), resendAfter)).isTrue();
        assertThat(order.getCancelLastSentAt()).isEqualTo(NOW.plusMinutes(10));
        assertThat(order.resendCancelRequest(NOW.plusMinutes(15), resendAfter)).isFalse();
    }

    @Test
    @DisplayName("배송 취소됨 결과는 현재 요청 번호와 같을 때만 취소를 확정한다")
    void confirmCancelOnlyForCurrentRequest() {
        Order order = personalOrder();
        order.markPaid(1L, NOW);
        order.cancelByBuyer(BUYER, NOW);

        assertThat(order.confirmCancel(2, NOW.plusMinutes(1))).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCEL_REQUESTED);

        assertThat(order.confirmCancel(1, NOW.plusMinutes(1))).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(order.getCancelReason()).isEqualTo(OrderCancelReason.BUYER_CANCELED);
        assertThat(order.confirmCancel(1, NOW.plusMinutes(2))).isFalse();
    }

    @Test
    @DisplayName("거절되면 결제 완료로 돌아가고, 다시 요청하면 번호가 오르며 이전 요청의 늦은 거절은 무시한다")
    void rejectThenRequestAgain() {
        Order order = personalOrder();
        order.markPaid(1L, NOW);
        order.cancelByBuyer(BUYER, NOW);

        assertThat(order.rejectCancel(1, "409-DELIVERY_NOT_CANCELABLE")).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getCancelRejectCode()).isEqualTo("409-DELIVERY_NOT_CANCELABLE");

        order.cancelByBuyer(BUYER, NOW.plusMinutes(3));
        assertThat(order.getCancelRequestSeq()).isEqualTo(2);
        assertThat(order.getCancelRejectCode()).isNull();

        assertThat(order.rejectCancel(1, "409-DELIVERY_NOT_CANCELABLE")).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCEL_REQUESTED);
    }

    @Test
    @DisplayName("취소 처리 중에 판매 중지로 모든 항목이 취소되면, 늦게 온 거절과 취소됨 결과는 모두 무시한다")
    void ignoreResultAfterWholeSalesStop() {
        Order order = Order.createPersonal("ORD-S", BUYER, List.of(
                new OrderLine(10L, 11L, 7L, "유니폼", null, 30_000L, 1)), addressWithoutDetail(), NOW);
        order.markPaid(1L, NOW);
        order.cancelByBuyer(BUYER, NOW.plusMinutes(1));

        order.cancelSellerItems(7L, DeliveryCancelReason.SALES_STOPPED, NOW.plusMinutes(2));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(order.rejectCancel(1, "409-DELIVERY_NOT_CANCELABLE")).isFalse();
        assertThat(order.confirmCancel(1, NOW.plusMinutes(3))).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(order.getCancelReason()).isEqualTo(OrderCancelReason.SALES_STOPPED);
        assertThat(order.getCancelRejectCode()).isNull();
    }

    @Test
    @DisplayName("취소 처리 중에 판매 중지로 일부 항목만 취소되고 거절이 오면, 일부 취소된 결제 완료 주문으로 돌아간다")
    void rejectAfterPartialSalesStop() {
        Order order = twoSellerOrder();
        order.markPaid(1L, NOW);
        order.cancelByBuyer(BUYER, NOW.plusMinutes(1));

        assertThat(order.cancelSellerItems(7L, DeliveryCancelReason.SALES_STOPPED, NOW.plusMinutes(2))).isEqualTo(30_000L);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCEL_REQUESTED);

        assertThat(order.rejectCancel(1, "409-DELIVERY_NOT_CANCELABLE")).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.isPartiallyCanceled()).isTrue();
        assertThat(order.getCancelRejectCode()).isEqualTo("409-DELIVERY_NOT_CANCELABLE");
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

    private Order twoSellerOrder() {
        return Order.createPersonal("ORD-C", BUYER, List.of(
                new OrderLine(10L, 11L, 7L, "유니폼", null, 30_000L, 1),
                new OrderLine(30L, 31L, 8L, "축구공", null, 20_000L, 1)
        ), addressWithoutDetail(), NOW);
    }

    @Test
    @DisplayName("쿠폰: 할인을 기록하면 결제 금액이 줄고, 같은 쿠폰·같은 금액으로 다시 와도 그대로, 다른 쿠폰이면 거절")
    void applyCoupon() {
        Order order = twoSellerOrder();

        order.applyCoupon(5L, 10_000L, true, null);

        assertThat(order.payAmount()).isEqualTo(40_000L);
        assertThat(order.getDiscountBurden()).isEqualTo(OrderDiscountBurden.PLATFORM);
        order.applyCoupon(5L, 10_000L, true, null);
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
        order.applyCoupon(5L, 10_000L, true, null);
        order.markPaid(99L, NOW.plusMinutes(1));

        long first = order.cancelSellerItems(8L, DeliveryCancelReason.SALES_STOPPED, NOW.plusMinutes(2));
        long second = order.cancelSellerItems(7L, DeliveryCancelReason.SALES_STOPPED, NOW.plusMinutes(3));

        assertThat(first).isEqualTo(16_000L);
        assertThat(second).isEqualTo(24_000L);
        assertThat(first + second).isEqualTo(order.payAmount());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(order.couponRestorable()).isFalse();
    }

    @Test
    @DisplayName("부분 취소 + 판매자 쿠폰: 그 판매자 상품 취소에만 할인을 빼고, 다른 판매자 상품은 전액 환불")
    void cancelSellerItemsWithSellerCoupon() {
        Order order = twoSellerOrder();
        order.applyCoupon(5L, 3_000L, false, 7L);
        order.markPaid(99L, NOW.plusMinutes(1));

        assertThat(order.cancelSellerItems(8L, DeliveryCancelReason.SALES_STOPPED, NOW.plusMinutes(2))).isEqualTo(20_000L);
        assertThat(order.cancelSellerItems(7L, DeliveryCancelReason.SALES_STOPPED, NOW.plusMinutes(3))).isEqualTo(27_000L);
    }

    @Test
    @DisplayName("부분 취소 + 나눗셈 끝전: 각 취소는 원 미만 버림, 마지막에 남은 금액을 돌려줘 합계가 결제 금액과 같다")
    void cancelSellerItemsRemainder() {
        Order order = Order.createPersonal("ORD-R", BUYER, List.of(
                new OrderLine(10L, 11L, 7L, "A", null, 10_000L, 1),
                new OrderLine(20L, 21L, 8L, "B", null, 10_000L, 1),
                new OrderLine(30L, 31L, 9L, "C", null, 10_000L, 1)
        ), addressWithoutDetail(), NOW);
        order.applyCoupon(5L, 1_000L, true, null);
        order.markPaid(99L, NOW.plusMinutes(1));

        long a = order.cancelSellerItems(7L, DeliveryCancelReason.SALES_STOPPED, NOW.plusMinutes(2));
        long b = order.cancelSellerItems(8L, DeliveryCancelReason.SALES_STOPPED, NOW.plusMinutes(3));
        long c = order.cancelSellerItems(9L, DeliveryCancelReason.SALES_STOPPED, NOW.plusMinutes(4));

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
        assertThat(byBuyer.couponRestorable()).isFalse();
        byBuyer.confirmCancel(1, NOW.plusMinutes(3));
        assertThat(byBuyer.couponRestorable()).isTrue();

        Order oneSeller = Order.createPersonal("ORD-S", BUYER, List.of(
                new OrderLine(10L, 11L, 7L, "유니폼", null, 30_000L, 1)), addressWithoutDetail(), NOW);
        oneSeller.applyCoupon(5L, 3_000L, true, null);
        oneSeller.markPaid(99L, NOW.plusMinutes(1));
        assertThat(oneSeller.cancelSellerItems(7L, DeliveryCancelReason.SALES_STOPPED, NOW.plusMinutes(2))).isEqualTo(27_000L);
        assertThat(oneSeller.couponRestorable()).isTrue();
    }

    @Test
    @DisplayName("판매 중지로 판매자 항목을 취소하면 그 판매자 항목 금액을 모두 더해 돌려주고, 다른 판매자 항목은 남는다 (ISS-26)")
    void cancelSellerItemsSumsAllItemsOfSeller() {
        Order order = Order.createPersonal("ORD-3", BUYER, List.of(
                new OrderLine(10L, 11L, 12L, "상품A", null, 10_000L, 2),
                new OrderLine(30L, 31L, 12L, "상품C", null, 3_000L, 1),
                new OrderLine(20L, 21L, 99L, "상품B", null, 5_000L, 1)), addressWithoutDetail(), NOW);
        order.markPaid(1L, NOW);

        long amount = order.cancelSellerItems(12L, DeliveryCancelReason.SALES_STOPPED, NOW);

        assertThat(amount).isEqualTo(23_000L);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.cancelSellerItems(12L, DeliveryCancelReason.SALES_STOPPED, NOW)).isZero();
    }

    private Order personalOrder() {
        return Order.createPersonal("ORD-1", BUYER, lines(), addressWithoutDetail(), NOW);
    }

    private Order individualGroupOrder(LocalDateTime saleDeadlineAt) {
        return Order.createIndividualGroup("ORD-2", BUYER, INDIVIDUAL_GROUP_PURCHASE_ID, saleDeadlineAt,
                lines(), addressWithoutDetail(), NOW);
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

    @Test
    @DisplayName("판매자 사유 취소: 모든 상품이 취소되면 주문은 판매자 사유 취소로 취소되고, 항목에는 판매자가 고른 사유가 남는다")
    void cancelSellerItemsBySellerReasonCancelsOrderWithSellerCanceled() {
        Order order = Order.createPersonal("ORD-5", BUYER, List.of(
                new OrderLine(10L, 11L, 7L, "유니폼", null, 30_000L, 1)
        ), addressWithoutDetail(), NOW);
        order.markPaid(99L, NOW.plusMinutes(1));

        assertThat(order.cancelSellerItems(7L, DeliveryCancelReason.SELLER_OUT_OF_STOCK, NOW.plusMinutes(2)))
                .isEqualTo(30_000L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(order.getCancelReason()).isEqualTo(OrderCancelReason.SELLER_CANCELED);
        assertThat(order.getItems()).extracting(OrderItem::getCancelReason)
                .containsExactly(DeliveryCancelReason.SELLER_OUT_OF_STOCK);
    }

    @Test
    @DisplayName("판매 중지로 먼저 취소된 판매자 몫에 판매자 사유 취소가 와도 0원이고, 항목 사유는 판매 중지로 남는다")
    void sellerReasonAfterSalesStopDoesNothing() {
        Order order = Order.createPersonal("ORD-6", BUYER, List.of(
                new OrderLine(10L, 11L, 7L, "유니폼", null, 30_000L, 1),
                new OrderLine(30L, 31L, 8L, "축구공", null, 20_000L, 1)
        ), addressWithoutDetail(), NOW);
        order.markPaid(99L, NOW.plusMinutes(1));
        order.cancelSellerItems(7L, DeliveryCancelReason.SALES_STOPPED, NOW.plusMinutes(2));

        assertThat(order.cancelSellerItems(7L, DeliveryCancelReason.SELLER_OTHER, NOW.plusMinutes(3))).isZero();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getItems()).extracting(OrderItem::getCancelReason)
                .containsExactly(DeliveryCancelReason.SALES_STOPPED, null);
    }
}
