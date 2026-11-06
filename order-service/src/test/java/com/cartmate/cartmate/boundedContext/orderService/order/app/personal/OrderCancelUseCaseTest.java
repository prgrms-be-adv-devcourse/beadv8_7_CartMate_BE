package com.cartmate.cartmate.boundedContext.orderService.order.app.personal;

import com.cartmate.cartmate.global.http.InternalApiClient.InternalServerUnavailableException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cartmate.cartmate.boundedContext.orderService.order.exception.OrderErrorCode;

import com.cartmate.cartmate.global.exception.ServiceException;

import com.cartmate.cartmate.shared.payment.dto.PaymentResult;

import com.cartmate.cartmate.shared.payment.dto.PaymentOrderType;

import com.cartmate.cartmate.boundedContext.orderService.order.out.OrderPaymentClient;

import com.cartmate.cartmate.shared.order.OrderCancelReason;

import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.Order;
import com.cartmate.cartmate.shared.order.OrderStatus;
import com.cartmate.cartmate.boundedContext.orderService.order.out.personal.OrderRepository;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static com.cartmate.cartmate.boundedContext.orderService.order.support.OrderFixtures.ballLine;
import static com.cartmate.cartmate.boundedContext.orderService.order.support.OrderFixtures.personalOrder;
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

    private OrderCancelUseCase cancelUseCase;

    @BeforeEach
    void setUp() {
        cancelUseCase = new OrderCancelUseCase(orderRepository, new OrderSupport(orderRepository), eventPublisher, paymentClient, CLOCK);
    }

    @Test
    @DisplayName("결제한 주문을 주문자가 취소하면 환불이 필요하다고 알리는 취소 이벤트를 발행한다")
    void cancelPaidOrderByBuyer() {
        Order order = personalOrder(BUYER, ballLine(null, 1), LocalDateTime.now(CLOCK));
        order.markPaid(1L, LocalDateTime.now(CLOCK));
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

        cancelUseCase.cancelByBuyer(BUYER, ORDER_ID);

        ArgumentCaptor<OrderCanceledEvent> captor = ArgumentCaptor.forClass(OrderCanceledEvent.class);
        then(eventPublisher).should().publish(captor.capture());
        OrderCanceledEvent event = captor.getValue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(event.memberId()).isEqualTo(BUYER);
        assertThat(event.cancelReason()).isEqualTo(OrderCancelReason.BUYER_CANCELED);
        assertThat(event.paid()).isTrue();
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
        org.springframework.test.util.ReflectionTestUtils.setField(order, "id", ORDER_ID); // 저장 전이라 id가 없다
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
        org.springframework.test.util.ReflectionTestUtils.setField(order, "id", ORDER_ID); // 저장 전이라 id가 없다
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
}
