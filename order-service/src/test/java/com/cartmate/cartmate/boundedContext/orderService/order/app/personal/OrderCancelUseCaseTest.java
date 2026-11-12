package com.cartmate.cartmate.boundedContext.orderService.order.app.personal;

import com.cartmate.cartmate.shared.order.event.OrderSellerItemsCanceledEvent;
import com.cartmate.cartmate.shared.delivery.DeliveryCancelReason;
import com.cartmate.cartmate.global.http.InternalApiClient.InternalServerUnavailableException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cartmate.cartmate.boundedContext.orderService.order.exception.OrderErrorCode;

import com.cartmate.cartmate.global.exception.ServiceException;

import com.cartmate.cartmate.shared.payment.dto.PaymentResult;

import com.cartmate.cartmate.shared.payment.dto.PaymentOrderType;

import com.cartmate.cartmate.boundedContext.orderService.order.out.OrderCouponClient;
import com.cartmate.cartmate.boundedContext.orderService.order.out.OrderPaymentClient;

import com.cartmate.cartmate.shared.order.OrderCancelReason;

import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.BuyerCancelResult;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.Order;
import com.cartmate.cartmate.shared.order.OrderStatus;
import com.cartmate.cartmate.boundedContext.orderService.order.out.personal.OrderRepository;
import com.cartmate.cartmate.shared.order.event.OrderCancelRequestedEvent;
import com.cartmate.cartmate.shared.order.event.OrderCanceledEvent;
import com.cartmate.cartmate.global.event.DomainEvent;
import com.cartmate.cartmate.global.event.DomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static com.cartmate.cartmate.boundedContext.orderService.order.support.OrderFixtures.ballLine;
import static com.cartmate.cartmate.boundedContext.orderService.order.support.OrderFixtures.personalOrder;
import static com.cartmate.cartmate.boundedContext.orderService.order.support.OrderFixtures.productBLine;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class OrderCancelUseCaseTest {

    private static final Long ORDER_ID = 100L;
    private static final Long BUYER = 1L;
    private static final Clock CLOCK = Clock.fixed(
            LocalDateTime.of(2026, 10, 19, 12, 0).atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private DomainEventPublisher eventPublisher;

    @Mock
    private OrderPaymentClient paymentClient;

    @Mock
    private OrderCouponClient couponClient;

    private OrderCancelUseCase cancelUseCase;

    @BeforeEach
    void setUp() {
        cancelUseCase = new OrderCancelUseCase(orderRepository, new OrderSupport(orderRepository), eventPublisher,
                couponClient, paymentClient, CLOCK);
    }

    @Test
    @DisplayName("결제한 주문을 주문자가 취소하면 바로 취소하지 않고 배송 서버에 취소 요청 이벤트를 발행한다")
    void requestCancelOfPaidOrder() {
        Order order = paidOrder();
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

        BuyerCancelResult result = cancelUseCase.cancelByBuyer(BUYER, ORDER_ID);

        assertThat(result).isEqualTo(BuyerCancelResult.REQUESTED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCEL_REQUESTED);
        then(eventPublisher).should().publish(
                new OrderCancelRequestedEvent(ORDER_ID, BUYER, 1, LocalDateTime.now(CLOCK)));
        then(eventPublisher).should(never()).publish(any(OrderCanceledEvent.class));
        then(couponClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("배송 취소됨 결과를 받으면 취소를 확정하고 환불이 필요하다고 알리는 취소 이벤트를 발행한다")
    void confirmCancelPublishesRefundEvent() {
        Order order = paidOrder();
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
        cancelUseCase.cancelByBuyer(BUYER, ORDER_ID);

        cancelUseCase.confirmCancelByDelivery(ORDER_ID, 1);

        ArgumentCaptor<OrderCanceledEvent> captor = ArgumentCaptor.forClass(OrderCanceledEvent.class);
        then(eventPublisher).should().publish(captor.capture());
        OrderCanceledEvent event = captor.getValue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(event.memberId()).isEqualTo(BUYER);
        assertThat(event.cancelReason()).isEqualTo(OrderCancelReason.BUYER_CANCELED);
        assertThat(event.paid()).isTrue();
        assertThat(event.canceledAt()).isEqualTo(LocalDateTime.now(CLOCK));
    }

    @Test
    @DisplayName("배송 취소 거절 결과를 받으면 결제 완료로 돌아가고 거절 코드를 남기며 환불 이벤트를 내지 않는다")
    void rejectCancelRestoresPaid() {
        Order order = paidOrder();
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
        cancelUseCase.cancelByBuyer(BUYER, ORDER_ID);

        cancelUseCase.rejectCancelByDelivery(ORDER_ID, 1, "409-DELIVERY_NOT_CANCELABLE");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getCancelRejectCode()).isEqualTo("409-DELIVERY_NOT_CANCELABLE");
        then(eventPublisher).should(never()).publish(any(OrderCanceledEvent.class));
    }

    @Test
    @DisplayName("요청 번호가 다른 늦은 결과는 반영하지 않는다")
    void ignoreResultOfOtherRequest() {
        Order order = paidOrder();
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
        cancelUseCase.cancelByBuyer(BUYER, ORDER_ID);

        cancelUseCase.confirmCancelByDelivery(ORDER_ID, 2);
        cancelUseCase.rejectCancelByDelivery(ORDER_ID, 2, "409-DELIVERY_NOT_CANCELABLE");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCEL_REQUESTED);
        then(eventPublisher).should(never()).publish(any(OrderCanceledEvent.class));
    }

    @Test
    @DisplayName("마지막 발송 뒤 정한 시간이 지난 취소 요청은 같은 번호와 처음 요청 시각으로 다시 발행한다")
    void resendStaleCancelRequest() {
        Order order = paidOrder();
        LocalDateTime firstRequestedAt = LocalDateTime.now(CLOCK).minusMinutes(11);
        order.cancelByBuyer(BUYER, firstRequestedAt);
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

        cancelUseCase.resendCancelRequest(ORDER_ID, Duration.ofMinutes(10));
        cancelUseCase.resendCancelRequest(ORDER_ID, Duration.ofMinutes(10));

        then(eventPublisher).should().publish(new OrderCancelRequestedEvent(ORDER_ID, BUYER, 1, firstRequestedAt));
        assertThat(order.getCancelLastSentAt()).isEqualTo(LocalDateTime.now(CLOCK));
    }

    private Order paidOrder() {
        Order order = personalOrder(BUYER, ballLine(null, 1), LocalDateTime.now(CLOCK));
        org.springframework.test.util.ReflectionTestUtils.setField(order, "id", ORDER_ID);
        order.markPaid(1L, LocalDateTime.now(CLOCK));
        return order;
    }

    @Test
    @DisplayName("결제 기한 전에는 기한 만료 취소를 하지 않고 이벤트도 발행하지 않는다")
    void cancelByDeadlineBeforeDeadline() {
        Order order = personalOrder(BUYER, ballLine(null, 1), LocalDateTime.now(CLOCK));
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

        cancelUseCase.cancelByDeadline(ORDER_ID);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        then(eventPublisher).should(never()).publish(any(DomainEvent.class));
    }

    @Test
    @DisplayName("결제 대기 주문을 취소할 때 차감됐지만 반영 안 된 결제가 있으면 되돌린 뒤 취소한다 (ISS-27)")
    void releaseUnappliedChargeOnCancel() {
        Order order = order();
        org.springframework.test.util.ReflectionTestUtils.setField(order, "id", ORDER_ID);
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
        given(paymentClient.findPayment(PaymentOrderType.INDIVIDUAL_GROUP, ORDER_ID, BUYER))
                .willReturn(Optional.of(paid())).willReturn(Optional.empty());

        cancelUseCase.cancelByBuyer(BUYER, ORDER_ID);

        then(paymentClient).should().compensateCharge(PaymentOrderType.INDIVIDUAL_GROUP, ORDER_ID, BUYER);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        ArgumentCaptor<OrderCanceledEvent> captor = ArgumentCaptor.forClass(OrderCanceledEvent.class);
        then(eventPublisher).should().publish(captor.capture());
        assertThat(captor.getValue().paid()).isFalse();
    }

    @Test
    @DisplayName("되돌린 뒤에도 결제가 남아 있으면 취소를 롤백하도록 예외를 던지고 이벤트를 내지 않는다 (돈이 빠진 채 취소 금지)")
    void rejectCancelWhenChargeStillPaid() {
        Order order = order();
        org.springframework.test.util.ReflectionTestUtils.setField(order, "id", ORDER_ID);
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
        given(paymentClient.findPayment(PaymentOrderType.INDIVIDUAL_GROUP, ORDER_ID, BUYER)).willReturn(Optional.of(paid()));

        assertThatThrownBy(() -> cancelUseCase.cancelByBuyer(BUYER, ORDER_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_PAYMENT_RELEASE_PENDING.getMsg());
        then(eventPublisher).should(never()).publish(any(DomainEvent.class));
    }

    @Test
    @DisplayName("결제 완료 주문 취소는 결제 서버에 남은 차감을 묻지 않는다 (환불은 payment가 취소 이벤트로)")
    void paidCancelDoesNotLookUpCharge() {
        Order order = order();
        order.markPaid(1L, LocalDateTime.now(CLOCK));
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

        cancelUseCase.cancelByBuyer(BUYER, ORDER_ID);

        then(paymentClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("결제 서버를 조회할 수 없으면(503) 취소를 진행하지 않고 그 예외를 그대로 던진다 — 돈이 빠졌는지 모른 채 취소 금지")
    void propagateOutageOnCancel() {
        Order order = order();
        org.springframework.test.util.ReflectionTestUtils.setField(order, "id", ORDER_ID);
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
        given(paymentClient.findPayment(PaymentOrderType.INDIVIDUAL_GROUP, ORDER_ID, BUYER))
                .willThrow(new InternalServerUnavailableException("PAYMENT", "결제", null));

        assertThatThrownBy(() -> cancelUseCase.cancelByBuyer(BUYER, ORDER_ID))
                .isInstanceOf(InternalServerUnavailableException.class);
        then(eventPublisher).should(never()).publish(any(DomainEvent.class));
        then(paymentClient).should(never()).compensateCharge(any(), any(), any());
    }

    private PaymentResult paid() {
        return new PaymentResult(1L, ORDER_ID, 25000L, 0L, 25000L, "INDIVIDUAL_GROUP", "PAID",
                LocalDateTime.now(CLOCK), null);
    }

    private Order order() {
        return personalOrder(BUYER, ballLine(null, 1), LocalDateTime.now(CLOCK));
    }

    @Test
    @DisplayName("쿠폰을 쓴 결제 완료 주문은 취소 요청 때가 아니라 취소가 확정될 때 취소 이벤트를 낸 뒤 쿠폰을 복구 요청한다")
    void confirmCancelOfCouponOrderRestoresCoupon() {
        Order order = couponPaidOrder();
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

        cancelUseCase.cancelByBuyer(BUYER, ORDER_ID);
        then(couponClient).shouldHaveNoInteractions();

        cancelUseCase.confirmCancelByDelivery(ORDER_ID, 1);

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(eventPublisher, couponClient);
        inOrder.verify(eventPublisher).publish(any(OrderCanceledEvent.class));
        inOrder.verify(couponClient).restore(BUYER, 5L, ORDER_ID);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    @DisplayName("취소 확정 중 쿠폰 복구 요청에 상품 서버가 응답하지 않으면 예외를 그대로 던져 확정 전체를 롤백한다 (Inbox가 다시 시도)")
    void confirmCancelFailsWhenCouponRestoreUnavailable() {
        Order order = couponPaidOrder();
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
        cancelUseCase.cancelByBuyer(BUYER, ORDER_ID);
        org.mockito.BDDMockito.willThrow(new InternalServerUnavailableException("PRODUCT", "상품", new RuntimeException("timeout")))
                .given(couponClient).restore(BUYER, 5L, ORDER_ID);

        assertThatThrownBy(() -> cancelUseCase.confirmCancelByDelivery(ORDER_ID, 1))
                .isInstanceOf(InternalServerUnavailableException.class);
    }

    @Test
    @DisplayName("판매글 내림으로 한 판매자 몫이 먼저 환불된 주문을 취소 확정하면 남은 금액만 환불 금액으로 싣는다")
    void confirmCancelCarriesRemainingRefundAmount() {
        Order order = personalOrder("20261019-TWOSELLR", BUYER,
                java.util.List.of(ballLine(10L, 1), productBLine(3L, 1)), LocalDateTime.now(CLOCK));
        org.springframework.test.util.ReflectionTestUtils.setField(order, "id", ORDER_ID);
        order.markPaid(1L, LocalDateTime.now(CLOCK));
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
        order.cancelSellerItems(7L, DeliveryCancelReason.SALES_STOPPED, LocalDateTime.now(CLOCK));
        cancelUseCase.cancelByBuyer(BUYER, ORDER_ID);

        cancelUseCase.confirmCancelByDelivery(ORDER_ID, 1);

        ArgumentCaptor<OrderCanceledEvent> captor = ArgumentCaptor.forClass(OrderCanceledEvent.class);
        then(eventPublisher).should().publish(captor.capture());
        assertThat(captor.getValue().refundAmount()).isEqualTo(25_000L);
    }

    @Test
    @DisplayName("결제 전 주문의 취소 이벤트에는 환불 금액이 없다")
    void unpaidCancelHasNoRefundAmount() {
        Order order = order();
        org.springframework.test.util.ReflectionTestUtils.setField(order, "id", ORDER_ID);
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
        given(paymentClient.findPayment(PaymentOrderType.INDIVIDUAL_GROUP, ORDER_ID, BUYER)).willReturn(Optional.empty());

        cancelUseCase.cancelByBuyer(BUYER, ORDER_ID);

        ArgumentCaptor<OrderCanceledEvent> captor = ArgumentCaptor.forClass(OrderCanceledEvent.class);
        then(eventPublisher).should().publish(captor.capture());
        assertThat(captor.getValue().refundAmount()).isNull();
    }

    private Order couponPaidOrder() {
        Order order = personalOrder(BUYER, ballLine(null, 1), LocalDateTime.now(CLOCK));
        org.springframework.test.util.ReflectionTestUtils.setField(order, "id", ORDER_ID);
        order.applyCoupon(5L, 1_000L, true, null);
        order.markPaid(1L, LocalDateTime.now(CLOCK));
        return order;
    }

    @Test
    @DisplayName("판매자 사유로 한 판매자 몫을 취소하면 환불 금액과 사유를 실은 판매자 몫 취소 이벤트를 발행한다")
    void cancelSellerItemsPublishesReason() {
        Order order = personalOrder("20261019-TWOSELLR", BUYER,
                java.util.List.of(ballLine(10L, 1), productBLine(3L, 1)), LocalDateTime.now(CLOCK));
        org.springframework.test.util.ReflectionTestUtils.setField(order, "id", ORDER_ID);
        order.markPaid(1L, LocalDateTime.now(CLOCK));
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

        cancelUseCase.cancelSellerItems(ORDER_ID, 7L, DeliveryCancelReason.SELLER_OUT_OF_STOCK);

        then(eventPublisher).should().publish(new OrderSellerItemsCanceledEvent(
                ORDER_ID, BUYER, 7L, 15_000L, LocalDateTime.now(CLOCK), DeliveryCancelReason.SELLER_OUT_OF_STOCK));
    }
}
