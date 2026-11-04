package com.cartmate.cartmate.boundedContext.order.in.groupshared;

import com.cartmate.cartmate.boundedContext.order.app.groupshared.SharedGroupOrderFacade;
import com.cartmate.cartmate.boundedContext.order.exception.OrderErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.shared.cart.event.SharedGroupPurchaseClosedEvent;
import com.cartmate.cartmate.shared.event.DomainEvent;
import com.cartmate.cartmate.shared.event.EventEnvelope;
import com.cartmate.cartmate.shared.event.OutboxMessageRelayed;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCreatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class SharedGroupOrderEventListenerTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 4, 12, 0);

    @Mock
    private SharedGroupOrderFacade sharedGroupOrderFacade;

    @InjectMocks
    private SharedGroupOrderEventListener listener;

    private final SharedGroupPurchaseClosedEvent closed = new SharedGroupPurchaseClosedEvent(
            10L, 1L, List.of(1L, 2L), List.of(new SharedGroupPurchaseClosedEvent.Item(2L, 4L, 1)), AT);

    @Test
    @DisplayName("마감 이벤트를 받으면 공동 주문을 만든다")
    void createsOrderFromClosedEvent() {
        listener.handle(relayed(closed));

        then(sharedGroupOrderFacade).should().createFromClosedPurchase(closed);
        then(sharedGroupOrderFacade).should(never()).reportCreationFailed(anyLong(), anyString());
    }

    @Test
    @DisplayName("다른 이벤트는 그냥 넘긴다")
    void ignoresOtherEvents() {
        listener.handle(relayed(new SharedGroupOrderCreatedEvent(5L, 10L, AT)));

        then(sharedGroupOrderFacade).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("업무 규칙 실패면 실패 소식을 내고 예외를 올리지 않는다(다시 해도 같으므로 재시도하지 않음)")
    void businessFailureIsReported() {
        willThrow(new ServiceException(OrderErrorCode.ORDER_PRODUCT_NOT_FOUND))
                .given(sharedGroupOrderFacade).createFromClosedPurchase(any());

        listener.handle(relayed(closed));

        then(sharedGroupOrderFacade).should()
                .reportCreationFailed(10L, OrderErrorCode.ORDER_PRODUCT_NOT_FOUND.resultCode());
    }

    @Test
    @DisplayName("그 밖의 실패(DB 순단 등)는 실패 소식 없이 예외를 릴레이로 올려 다시 시도하게 한다")
    void transientFailurePropagates() {
        willThrow(new QueryTimeoutException("순단")).given(sharedGroupOrderFacade).createFromClosedPurchase(any());

        assertThatThrownBy(() -> listener.handle(relayed(closed))).isInstanceOf(QueryTimeoutException.class);

        then(sharedGroupOrderFacade).should(never()).reportCreationFailed(anyLong(), anyString());
    }

    private OutboxMessageRelayed relayed(DomainEvent event) {
        return new OutboxMessageRelayed(new EventEnvelope("event-1", event.getClass().getSimpleName(), "topic",
                event.aggregateType(), event.aggregateId(), AT, 1, "{}"), event);
    }
}
