package com.cartmate.cartmate.boundedContext.order.app.personal;

import com.cartmate.cartmate.boundedContext.order.domain.DeliveryAddress;
import com.cartmate.cartmate.boundedContext.order.domain.personal.Order;
import com.cartmate.cartmate.boundedContext.order.domain.personal.Order.OrderLine;
import com.cartmate.cartmate.boundedContext.order.domain.personal.OrderStatus;
import com.cartmate.cartmate.boundedContext.order.out.personal.OrderRepository;
import com.cartmate.cartmate.shared.order.event.OrderCanceledEvent;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class OrderCancelUseCaseTest {

    private static final Long ORDER_ID = 100L;
    private static final Long BUYER = 1L;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private OrderCancelUseCase cancelUseCase;

    @BeforeEach
    void setUp() {
        cancelUseCase = new OrderCancelUseCase(orderRepository, new OrderSupport(orderRepository), eventPublisher);
    }

    @Test
    @DisplayName("결제한 주문을 주문자가 취소하면 환불이 필요하다고 알리는 취소 이벤트를 발행한다")
    void cancelPaidOrderByBuyer() {
        Order order = order();
        order.markPaid(1L, LocalDateTime.now());
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

        cancelUseCase.cancelByBuyer(BUYER, ORDER_ID);

        ArgumentCaptor<OrderCanceledEvent> captor = ArgumentCaptor.forClass(OrderCanceledEvent.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        OrderCanceledEvent event = captor.getValue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(event.memberId()).isEqualTo(BUYER);
        assertThat(event.cancelReason()).isEqualTo("BUYER_CANCELED");
        assertThat(event.paid()).isTrue();
    }

    @Test
    @DisplayName("결제 기한 전에는 기한 만료 취소를 하지 않고 이벤트도 발행하지 않는다")
    void cancelByDeadlineBeforeDeadline() {
        Order order = order();
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

        cancelUseCase.cancelByDeadline(ORDER_ID);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        then(eventPublisher).should(never()).publishEvent(any(Object.class));
    }

    private Order order() {
        return Order.createPersonal("20261019-ABCDEFGH", BUYER,
                List.of(new OrderLine(2L, 4L, null, "축구공", "5호", 25000, 1)),
                DeliveryAddress.of("홍길동", "010-1234-5678", "12345", "서울시 강남구", null), LocalDateTime.now());
    }
}
