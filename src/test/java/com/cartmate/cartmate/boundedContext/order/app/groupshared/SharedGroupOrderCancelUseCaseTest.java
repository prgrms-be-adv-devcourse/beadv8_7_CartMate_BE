package com.cartmate.cartmate.boundedContext.order.app.groupshared;

import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrder;
import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrder.OrderItemLine;
import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrderCancelReason;
import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrderStatus;
import com.cartmate.cartmate.boundedContext.order.exception.OrderErrorCode;
import com.cartmate.cartmate.boundedContext.order.out.groupshared.SharedGroupOrderRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCanceledEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;

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
    private SharedGroupOrderRepository sharedGroupOrderRepository;

    @Mock
    private SharedGroupOrderSupport sharedGroupOrderSupport;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private SharedGroupOrderCancelUseCase cancelUseCase;

    @Test
    @DisplayName("생성자가 취소하면 주문을 취소하고 취소 이벤트를 한 번 발행한다")
    void cancelByCreatorPublishesEvent() {
        SharedGroupOrder order = order(LocalDateTime.now());
        given(sharedGroupOrderSupport.getOrderForUpdate(ORDER_ID)).willReturn(order);

        cancelUseCase.cancelByCreator(CREATOR, ORDER_ID);

        ArgumentCaptor<SharedGroupOrderCanceledEvent> captor = ArgumentCaptor.forClass(SharedGroupOrderCanceledEvent.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        SharedGroupOrderCanceledEvent event = captor.getValue();
        assertThat(order.getStatus()).isEqualTo(SharedGroupOrderStatus.CANCELED);
        assertThat(event.sharedGroupPurchaseId()).isEqualTo(PURCHASE_ID);
        assertThat(event.cancelReason()).isEqualTo(SharedGroupOrderCancelReason.CREATOR_CANCELED.name());
        assertThat(event.canceledAt()).isEqualTo(order.getCanceledAt());
    }

    @Test
    @DisplayName("생성자가 아니면 취소하지 않고 이벤트도 발행하지 않는다")
    void cancelByNonCreator() {
        given(sharedGroupOrderSupport.getOrderForUpdate(ORDER_ID)).willReturn(order(LocalDateTime.now()));

        assertThatThrownBy(() -> cancelUseCase.cancelByCreator(MEMBER, ORDER_ID))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_CREATOR_ONLY.getMsg());
        then(eventPublisher).should(never()).publishEvent(any(Object.class));
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
        then(eventPublisher).should().publishEvent(captor.capture());
        assertThat(captor.getValue().cancelReason()).isEqualTo(SharedGroupOrderCancelReason.DEADLINE_EXPIRED.name());
        assertThat(order.getStatus()).isEqualTo(SharedGroupOrderStatus.CANCELED);
    }

    @Test
    @DisplayName("아직 결제 기한 전이면 취소하지 않고 이벤트도 발행하지 않는다")
    void cancelByDeadlineNotExpired() {
        SharedGroupOrder order = order(LocalDateTime.now());
        given(sharedGroupOrderSupport.getOrderForUpdate(ORDER_ID)).willReturn(order);

        cancelUseCase.cancelByDeadline(ORDER_ID);

        then(eventPublisher).should(never()).publishEvent(any(Object.class));
        assertThat(order.getStatus()).isEqualTo(SharedGroupOrderStatus.PAYING);
    }

    @Test
    @DisplayName("이미 취소된 주문은 기한 만료로 다시 취소하지 않고 이벤트도 발행하지 않는다")
    void cancelByDeadlineAlreadyCanceled() {
        LocalDateTime closedAt = LocalDateTime.now()
                .minus(SharedGroupOrder.PAYMENT_PERIOD)
                .minus(SharedGroupOrder.GRACE_PERIOD)
                .minusMinutes(1);
        SharedGroupOrder order = order(closedAt);
        order.cancelByOutOfStock(LocalDateTime.now());
        given(sharedGroupOrderSupport.getOrderForUpdate(ORDER_ID)).willReturn(order);

        cancelUseCase.cancelByDeadline(ORDER_ID);

        then(eventPublisher).should(never()).publishEvent(any(Object.class));
        assertThat(order.getCancelReason()).isEqualTo(SharedGroupOrderCancelReason.OUT_OF_STOCK);
    }

    @Test
    @DisplayName("재고 부족으로 취소하면 이벤트를 한 번 발행한다")
    void cancelByOutOfStockPublishesEvent() {
        SharedGroupOrder order = order(LocalDateTime.now());
        given(sharedGroupOrderSupport.getOrderForUpdate(ORDER_ID)).willReturn(order);

        cancelUseCase.cancelByOutOfStock(ORDER_ID);

        ArgumentCaptor<SharedGroupOrderCanceledEvent> captor = ArgumentCaptor.forClass(SharedGroupOrderCanceledEvent.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        assertThat(captor.getValue().cancelReason()).isEqualTo(SharedGroupOrderCancelReason.OUT_OF_STOCK.name());
    }

    @Test
    @DisplayName("기한 만료 취소 대상 id는 Repository에서 조회한다")
    void findIdsToCancelByDeadline() {
        LocalDateTime now = LocalDateTime.now();
        given(sharedGroupOrderRepository.findIdsToCancelByDeadline(now)).willReturn(List.of(1L, 2L));

        assertThat(cancelUseCase.findIdsToCancelByDeadline(now)).containsExactly(1L, 2L);
    }

    private SharedGroupOrder order(LocalDateTime closedAt) {
        return SharedGroupOrder.create(PURCHASE_ID, CREATOR, List.of(CREATOR, MEMBER),
                List.of(new OrderItemLine(2L, 4L, 1, 10000L)), closedAt);
    }
}
