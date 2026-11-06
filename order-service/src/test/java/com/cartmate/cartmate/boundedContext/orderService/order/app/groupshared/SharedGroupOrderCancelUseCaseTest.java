package com.cartmate.cartmate.boundedContext.orderService.order.app.groupshared;

import com.cartmate.cartmate.boundedContext.orderService.order.domain.DeliveryAddress;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.groupshared.SharedGroupOrder;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.groupshared.SharedGroupOrder.OrderItemLine;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.groupshared.SharedGroupOrderCancelReason;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.groupshared.SharedGroupOrderStatus;
import com.cartmate.cartmate.boundedContext.orderService.order.exception.OrderErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCanceledEvent;
import com.cartmate.cartmate.global.event.DomainEvent;
import com.cartmate.cartmate.global.event.DomainEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class SharedGroupOrderCancelUseCaseTest {

    private static final Long ORDER_ID = 100L;
    private static final Long PURCHASE_ID = 10L;
    private static final Long CREATOR = 1L;
    private static final Long MEMBER = 2L;

    @Mock
    private SharedGroupOrderSupport sharedGroupOrderSupport;

    @Mock
    private DomainEventPublisher eventPublisher;

    @InjectMocks
    private SharedGroupOrderCancelUseCase cancelUseCase;

    @Test
    @DisplayName("생성자가 취소하면 주문을 취소하고 취소 이벤트를 한 번 발행한다")
    void cancelByCreatorPublishesEvent() {
        SharedGroupOrder order = order(LocalDateTime.now());
        given(sharedGroupOrderSupport.getOrderForUpdate(ORDER_ID)).willReturn(order);

        cancelUseCase.cancelByCreator(CREATOR, ORDER_ID);

        ArgumentCaptor<SharedGroupOrderCanceledEvent> captor = ArgumentCaptor.forClass(SharedGroupOrderCanceledEvent.class);
        then(eventPublisher).should().publish(captor.capture());
        SharedGroupOrderCanceledEvent event = captor.getValue();
        assertThat(order.getStatus()).isEqualTo(SharedGroupOrderStatus.CANCELED);
        assertThat(event.sharedGroupPurchaseId()).isEqualTo(PURCHASE_ID);
        assertThat(event.cancelReason()).isEqualTo(SharedGroupOrderCancelReason.CREATOR_CANCELED.name());
        assertThat(event.canceledAt()).isEqualTo(order.getCanceledAt());
        assertThat(event.unpaidMemberIds()).containsExactly(CREATOR, MEMBER);
    }

    @Test
    @DisplayName("생성자가 아니면 취소하지 않고 이벤트도 발행하지 않는다")
    void cancelByNonCreator() {
        given(sharedGroupOrderSupport.getOrderForUpdate(ORDER_ID)).willReturn(order(LocalDateTime.now()));

        assertThatThrownBy(() -> cancelUseCase.cancelByCreator(MEMBER, ORDER_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_CREATOR_ONLY.getMsg());
        then(eventPublisher).should(never()).publish(any(DomainEvent.class));
    }

    @Test
    @DisplayName("결제 기한과 유예 시간이 지나면 기한 만료로 취소하고 이벤트를 한 번 발행한다")
    void cancelByDeadlinePublishesEvent() {
        LocalDateTime closedAt = LocalDateTime.now()
                .minus(SharedGroupOrder.PAYMENT_PERIOD)
                .minus(SharedGroupOrder.GRACE_PERIOD)
                .minusMinutes(1);
        SharedGroupOrder order = order(closedAt);
        given(sharedGroupOrderSupport.getOrderForUpdate(ORDER_ID)).willReturn(order);

        cancelUseCase.cancelByDeadline(ORDER_ID);

        ArgumentCaptor<SharedGroupOrderCanceledEvent> captor = ArgumentCaptor.forClass(SharedGroupOrderCanceledEvent.class);
        then(eventPublisher).should().publish(captor.capture());
        assertThat(captor.getValue().cancelReason()).isEqualTo(SharedGroupOrderCancelReason.DEADLINE_EXPIRED.name());
        assertThat(order.getStatus()).isEqualTo(SharedGroupOrderStatus.CANCELED);
    }

    @Test
    @DisplayName("기한 만료 취소 이벤트에는 결제하지 않은 참여자만 담긴다")
    void cancelByDeadlineEventHasOnlyUnpaidMembers() {
        LocalDateTime closedAt = LocalDateTime.now()
                .minus(SharedGroupOrder.PAYMENT_PERIOD)
                .minus(SharedGroupOrder.GRACE_PERIOD)
                .minusMinutes(1);
        SharedGroupOrder order = order(closedAt);
        order.markStockReserved();
        order.pay(CREATOR, 1L, closedAt.plusMinutes(1),
                DeliveryAddress.of("홍길동", "010-1234-5678", "12345", "서울시 강남구", "101호"));
        given(sharedGroupOrderSupport.getOrderForUpdate(ORDER_ID)).willReturn(order);

        cancelUseCase.cancelByDeadline(ORDER_ID);

        ArgumentCaptor<SharedGroupOrderCanceledEvent> captor = ArgumentCaptor.forClass(SharedGroupOrderCanceledEvent.class);
        then(eventPublisher).should().publish(captor.capture());
        assertThat(captor.getValue().unpaidMemberIds()).containsExactly(MEMBER);
    }

    @ParameterizedTest(name = "{0}", quoteTextArguments = false)
    @MethodSource("notCancelableByDeadline")
    @DisplayName("기한 만료 취소 대상이 아니면 주문 상태를 바꾸지 않고 이벤트도 발행하지 않는다")
    void cancelByDeadlineSkipped(String situation, SharedGroupOrder order,
                                 SharedGroupOrderStatus expectedStatus, SharedGroupOrderCancelReason expectedReason) {
        given(sharedGroupOrderSupport.getOrderForUpdate(ORDER_ID)).willReturn(order);

        cancelUseCase.cancelByDeadline(ORDER_ID);

        then(eventPublisher).should(never()).publish(any(DomainEvent.class));
        assertThat(order.getStatus()).isEqualTo(expectedStatus);
        assertThat(order.getCancelReason()).isEqualTo(expectedReason);
    }

    static Stream<Arguments> notCancelableByDeadline() {
        LocalDateTime expiredClosedAt = LocalDateTime.now()
                .minus(SharedGroupOrder.PAYMENT_PERIOD)
                .minus(SharedGroupOrder.GRACE_PERIOD)
                .minusMinutes(1);
        SharedGroupOrder alreadyCanceled = order(expiredClosedAt);
        alreadyCanceled.cancelByOutOfStock(LocalDateTime.now());
        return Stream.of(
                Arguments.of("아직 결제 기한 전이면 결제 중으로 남는다",
                        order(LocalDateTime.now()), SharedGroupOrderStatus.PAYING, null),
                Arguments.of("이미 재고 부족으로 취소됐으면 취소 사유가 바뀌지 않는다",
                        alreadyCanceled, SharedGroupOrderStatus.CANCELED, SharedGroupOrderCancelReason.OUT_OF_STOCK));
    }

    @Test
    @DisplayName("재고 부족으로 취소하면 이벤트를 한 번 발행한다")
    void cancelByOutOfStockPublishesEvent() {
        SharedGroupOrder order = order(LocalDateTime.now());
        given(sharedGroupOrderSupport.getOrderForUpdate(ORDER_ID)).willReturn(order);

        cancelUseCase.cancelByOutOfStock(ORDER_ID);

        ArgumentCaptor<SharedGroupOrderCanceledEvent> captor = ArgumentCaptor.forClass(SharedGroupOrderCanceledEvent.class);
        then(eventPublisher).should().publish(captor.capture());
        assertThat(captor.getValue().cancelReason()).isEqualTo(SharedGroupOrderCancelReason.OUT_OF_STOCK.name());
    }

    private static SharedGroupOrder order(LocalDateTime closedAt) {
        return SharedGroupOrder.create(PURCHASE_ID, CREATOR, List.of(CREATOR, MEMBER),
                List.of(new OrderItemLine(2L, 4L, null, "상품", null, 1, 10000L)), closedAt);
    }
}
