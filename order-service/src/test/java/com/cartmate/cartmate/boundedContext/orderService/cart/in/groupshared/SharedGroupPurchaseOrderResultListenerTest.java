package com.cartmate.cartmate.boundedContext.orderService.cart.in.groupshared;

import com.cartmate.cartmate.boundedContext.orderService.cart.app.groupshared.SharedGroupPurchaseFacade;
import com.cartmate.cartmate.shared.cart.event.SharedGroupPurchaseClosedEvent;
import com.cartmate.cartmate.global.event.DomainEvent;
import com.cartmate.cartmate.global.event.EventEnvelope;
import com.cartmate.cartmate.global.event.OutboxMessageRelayed;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class SharedGroupPurchaseOrderResultListenerTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 4, 12, 0);

    @Mock
    private SharedGroupPurchaseFacade sharedGroupPurchaseFacade;

    @InjectMocks
    private SharedGroupPurchaseOrderResultListener listener;

    @Test
    @DisplayName("다른 이벤트(자기가 낸 마감 이벤트 포함)는 그냥 넘긴다")
    void ignoresOthers() {
        listener.handle(relayed(new SharedGroupPurchaseClosedEvent(10L, 1L, List.of(1L), List.of(), AT)));

        then(sharedGroupPurchaseFacade).shouldHaveNoInteractions();
    }

    private OutboxMessageRelayed relayed(DomainEvent event) {
        return new OutboxMessageRelayed(new EventEnvelope("event-1", event.getClass().getSimpleName(), "topic",
                event.aggregateType(), event.aggregateId(), AT, 1, "{}"), event);
    }
}
