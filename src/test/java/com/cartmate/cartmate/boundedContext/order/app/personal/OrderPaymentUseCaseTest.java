package com.cartmate.cartmate.boundedContext.order.app.personal;

import com.cartmate.cartmate.boundedContext.order.domain.DeliveryAddress;
import com.cartmate.cartmate.boundedContext.order.domain.personal.Order;
import com.cartmate.cartmate.boundedContext.order.domain.personal.Order.OrderLine;
import com.cartmate.cartmate.boundedContext.order.domain.personal.OrderStatus;
import com.cartmate.cartmate.boundedContext.order.exception.OrderErrorCode;
import com.cartmate.cartmate.boundedContext.order.out.personal.OrderRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.order.event.OrderPaidEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class OrderPaymentUseCaseTest {

    private static final Long ORDER_ID = 100L;
    private static final Long BUYER = 1L;
    private static final Long PAYMENT_ID = 500L;
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 19, 12, 0);

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private OrderPaymentUseCase paymentUseCase;

    @BeforeEach
    void setUp() {
        paymentUseCase = new OrderPaymentUseCase(orderRepository, new OrderSupport(orderRepository), eventPublisher);
    }

    @Test
    @DisplayName("결제 기한 안에 결제하면 결제 완료로 바꾸고 결제 완료 이벤트를 한 번 발행한다")
    void applyPayment() {
        Order order = order();
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
        LocalDateTime paidAt = CREATED_AT.plusMinutes(10);

        paymentUseCase.applyPayment(ORDER_ID, BUYER, PAYMENT_ID, paidAt);

        ArgumentCaptor<OrderPaidEvent> captor = ArgumentCaptor.forClass(OrderPaidEvent.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        OrderPaidEvent event = captor.getValue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(event.memberId()).isEqualTo(BUYER);
        assertThat(event.orderType()).isEqualTo("PERSONAL");
        assertThat(event.paidAt()).isEqualTo(paidAt);
        assertThat(event.items())
                .extracting(OrderPaidEvent.Item::productId, OrderPaidEvent.Item::inventoryId,
                        OrderPaidEvent.Item::quantity, OrderPaidEvent.Item::unitPrice)
                .containsExactly(tuple(2L, 4L, 2, 25000L));
    }

    @Test
    @DisplayName("결제 기한이 지난 결제는 반영하지 않고 이벤트도 발행하지 않는다")
    void applyPaymentAfterDeadline() {
        Order order = order();
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentUseCase.applyPayment(ORDER_ID, BUYER, PAYMENT_ID,
                CREATED_AT.plus(Order.PAYMENT_PERIOD).plusSeconds(1)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_PAYMENT_EXPIRED.getMsg());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        then(eventPublisher).should(never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("같은 결제 ID로 결제 통보가 두 번 와도 예외 없이 결제 완료 이벤트는 한 번만 발행한다")
    void applyPaymentSamePaymentIdTwice() {
        Order order = order();
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
        LocalDateTime paidAt = CREATED_AT.plusMinutes(10);

        paymentUseCase.applyPayment(ORDER_ID, BUYER, PAYMENT_ID, paidAt);
        paymentUseCase.applyPayment(ORDER_ID, BUYER, PAYMENT_ID, paidAt);

        then(eventPublisher).should(times(1)).publishEvent(any(OrderPaidEvent.class));
        assertThat(order.getPaymentId()).isEqualTo(PAYMENT_ID);
    }

    @Test
    @DisplayName("이미 다른 결제 ID로 결제된 주문은 다시 결제할 수 없다")
    void applyPaymentDifferentPaymentId() {
        Order order = order();
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
        LocalDateTime paidAt = CREATED_AT.plusMinutes(10);
        paymentUseCase.applyPayment(ORDER_ID, BUYER, PAYMENT_ID, paidAt);

        assertThatThrownBy(() -> paymentUseCase.applyPayment(ORDER_ID, BUYER, PAYMENT_ID + 1, paidAt))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_NOT_PAYMENT_PENDING.getMsg());
        then(eventPublisher).should(times(1)).publishEvent(any(OrderPaidEvent.class));
    }

    private Order order() {
        return Order.createPersonal("20261019-ABCDEFGH", BUYER,
                List.of(new OrderLine(2L, 4L, null, "축구공", "5호", 25000, 2)),
                DeliveryAddress.of("홍길동", "010-1234-5678", "12345", "서울시 강남구", null), CREATED_AT);
    }
}
