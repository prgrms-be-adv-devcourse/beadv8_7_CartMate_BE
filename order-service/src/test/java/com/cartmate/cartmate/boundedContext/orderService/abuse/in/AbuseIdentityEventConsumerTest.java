package com.cartmate.cartmate.boundedContext.orderService.abuse.in;

import com.cartmate.cartmate.boundedContext.orderService.abuse.app.AbuseFacade;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseIdentityCommand;
import com.cartmate.cartmate.global.event.DomainEvent;
import com.cartmate.cartmate.global.event.EventEnvelope;
import com.cartmate.cartmate.global.event.OutboxMessageRelayed;
import com.cartmate.cartmate.shared.order.OrderType;
import com.cartmate.cartmate.shared.order.dto.DeliveryAddressInfo;
import com.cartmate.cartmate.shared.order.dto.TeamDeliveryAddress;
import com.cartmate.cartmate.shared.order.event.OrderPaidEvent;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderConfirmedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class AbuseIdentityEventConsumerTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 6, 12, 0);

    @Mock
    private AbuseFacade abuseFacade;

    private AbuseIdentityEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new AbuseIdentityEventConsumer(abuseFacade);
    }

    @Test
    @DisplayName("주문이 결제되면 주문자의 수령인 전화 · 배송지를 식별 정보로 넘긴다")
    void recordsBuyerIdentityOnPaid() {
        consumer.handle(relayed(new OrderPaidEvent(1L, "N-1", 7L, OrderType.PERSONAL, null, AT,
                new DeliveryAddressInfo("홍길동", "010-1111-2222", "06236", "서울 강남구", "101호"), List.of())));

        then(abuseFacade).should().recordIdentity(
                new AbuseIdentityCommand(7L, "010-1111-2222", "06236", "서울 강남구", "101호"));
    }

    @Test
    @DisplayName("공동 주문이 확정되면 방장의 팀 배송지를 식별 정보로 넘긴다")
    void recordsCreatorIdentityOnConfirmed() {
        consumer.handle(relayed(new SharedGroupOrderConfirmedEvent(100L, 10L, 3L, AT,
                new TeamDeliveryAddress("방장", "010-3333-4444", "04524", "서울 중구", "2층"), List.of())));

        then(abuseFacade).should().recordIdentity(
                new AbuseIdentityCommand(3L, "010-3333-4444", "04524", "서울 중구", "2층"));
    }

    @Test
    @DisplayName("배송지가 없거나 받지 않는 이벤트는 아무 것도 넘기지 않는다")
    void ignoresMissingAddressAndOtherEvents() {
        consumer.handle(relayed(new OrderPaidEvent(1L, "N-1", 7L, OrderType.PERSONAL, null, AT, null, List.of())));
        consumer.handle(relayed(mock(DomainEvent.class)));

        then(abuseFacade).shouldHaveNoInteractions();
    }

    private OutboxMessageRelayed relayed(DomainEvent event) {
        return new OutboxMessageRelayed(new EventEnvelope("event-id", "test", "test", "Order", "1", AT, 1, "{}"), event);
    }
}
