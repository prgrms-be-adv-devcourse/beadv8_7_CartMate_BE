package com.cartmate.cartmate.boundedContext.orderService.abuse.in;

import com.cartmate.cartmate.shared.order.SharedGroupOrderCancelReason;

import com.cartmate.cartmate.boundedContext.orderService.abuse.app.AbuseFacade;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignalCommand;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignalType;
import com.cartmate.cartmate.global.event.DomainEvent;
import com.cartmate.cartmate.global.event.EventEnvelope;
import com.cartmate.cartmate.global.event.OutboxMessageRelayed;
import com.cartmate.cartmate.shared.order.OrderCancelReason;
import com.cartmate.cartmate.shared.order.OrderType;
import com.cartmate.cartmate.shared.order.event.OrderCanceledEvent;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCanceledEvent;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderConfirmedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class AbuseOrderEventConsumerTest {

    private static final LocalDateTime CANCELED_AT = LocalDateTime.of(2026, 11, 4, 12, 0);

    private static final Long CREATOR = 1L;

    @Mock
    private AbuseFacade abuseFacade;

    @Mock
    private AbuseJudgeRunner abuseJudgeRunner;

    private AbuseOrderEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new AbuseOrderEventConsumer(new AbuseSignalIntake(abuseFacade, abuseJudgeRunner));
    }

    @Test
    @DisplayName("기한 만료 취소면 미결제 회원 신호를 모두 접수한 뒤 회원마다 한 번씩 판정한다")
    void receivesThenJudgesEachUnpaidMember() {
        consumer.handle(relayed(canceled(SharedGroupOrderCancelReason.DEADLINE_EXPIRED, Arrays.asList(2L, 3L, 2L))));

        InOrder order = inOrder(abuseFacade, abuseJudgeRunner);
        order.verify(abuseFacade).receive(command(2L));
        order.verify(abuseFacade).receive(command(3L));
        order.verify(abuseFacade).receive(command(2L));
        order.verify(abuseJudgeRunner).judge(2L);
        order.verify(abuseJudgeRunner).judge(3L);
        order.verifyNoMoreInteractions();
    }

    @Test
    @DisplayName("판정 중 일시 장애가 나면 남은 회원 판정을 멈추고 재판정 워커에 맡긴다")
    void stopsJudgingOnTransientFailure() {
        given(abuseJudgeRunner.judge(2L)).willReturn(AbuseJudgeOutcome.TRANSIENT_FAILURE);

        consumer.handle(relayed(canceled(SharedGroupOrderCancelReason.DEADLINE_EXPIRED, List.of(2L, 3L))));

        then(abuseFacade).should().receive(command(2L));
        then(abuseFacade).should().receive(command(3L));
        then(abuseJudgeRunner).should().judge(2L);
        then(abuseJudgeRunner).should(never()).judge(3L);
    }

    @Test
    @DisplayName("잘못된 회원 항목은 건너뛰고 나머지만 접수·판정한다")
    void skipsInvalidMember() {
        consumer.handle(relayed(canceled(SharedGroupOrderCancelReason.DEADLINE_EXPIRED, Arrays.asList(null, 3L))));

        then(abuseFacade).should().receive(command(3L));
        then(abuseJudgeRunner).should().judge(3L);
        then(abuseFacade).shouldHaveNoMoreInteractions();
        then(abuseJudgeRunner).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("접수가 실패하면 예외를 릴레이로 올려 다시 받게 하고 판정은 하지 않는다")
    void propagatesReceiveFailure() {
        willThrow(new IllegalStateException("abuse DB down")).given(abuseFacade).receive(any());

        assertThatThrownBy(() -> consumer.handle(relayed(canceled(SharedGroupOrderCancelReason.DEADLINE_EXPIRED, List.of(2L)))))
                .isInstanceOf(IllegalStateException.class);

        then(abuseJudgeRunner).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("일부 회원 접수가 실패하면 아무도 판정하지 않고, 다시 전달되면 전원을 접수한 뒤 판정한다")
    void judgesAllOnlyAfterRedeliveryWhenReceivePartiallyFails() {
        OutboxMessageRelayed message = relayed(canceled(SharedGroupOrderCancelReason.DEADLINE_EXPIRED, List.of(2L, 3L, 4L)));
        lenient().doThrow(new IllegalStateException("abuse DB down")).doNothing().when(abuseFacade).receive(command(3L));

        assertThatThrownBy(() -> consumer.handle(message)).isInstanceOf(IllegalStateException.class);
        then(abuseJudgeRunner).shouldHaveNoInteractions();

        consumer.handle(message);

        then(abuseFacade).should(times(2)).receive(command(2L));
        then(abuseFacade).should(times(2)).receive(command(3L));
        then(abuseFacade).should().receive(command(4L));
        then(abuseJudgeRunner).should().judge(2L);
        then(abuseJudgeRunner).should().judge(3L);
        then(abuseJudgeRunner).should().judge(4L);
    }

    @Test
    @DisplayName("신호가 아닌 취소 사유(판매 중지), 미결제자 목록이 없는 기한 만료, 다른 이벤트는 무시한다")
    void ignoresOtherMessages() {
        consumer.handle(relayed(canceled(SharedGroupOrderCancelReason.SALES_STOPPED, List.of(2L))));
        consumer.handle(relayed(canceled(SharedGroupOrderCancelReason.DEADLINE_EXPIRED, null)));
        consumer.handle(relayed(mock(SharedGroupOrderConfirmedEvent.class)));

        then(abuseFacade).shouldHaveNoInteractions();
        then(abuseJudgeRunner).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("방장이 공동 주문을 취소하면 방장에게 방장 취소 신호를 접수하고 판정한다. 미결제자는 신호가 없다")
    void receivesCreatorCanceledSignal() {
        consumer.handle(relayed(canceled(SharedGroupOrderCancelReason.CREATOR_CANCELED, List.of(2L, 3L))));

        then(abuseFacade).should().receive(new AbuseSignalCommand(CREATOR, AbuseSignalType.SHARED_PURCHASE_CREATOR_CANCELED,
                "order:100", CANCELED_AT, "sharedGroupPurchase:10"));
        then(abuseJudgeRunner).should().judge(CREATOR);
        then(abuseFacade).shouldHaveNoMoreInteractions();
        then(abuseJudgeRunner).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("방장 ID가 없는 예전 방장 취소 이벤트는 무시한다")
    void ignoresCreatorCanceledWithoutCreator() {
        consumer.accept(new SharedGroupOrderCanceledEvent(100L, 10L, SharedGroupOrderCancelReason.CREATOR_CANCELED,
                CANCELED_AT, List.of(2L), null));

        then(abuseFacade).shouldHaveNoInteractions();
        then(abuseJudgeRunner).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("개인물품 공동구매 주문이 결제 기한 만료로 취소되면 주문자 신호를 접수하고, 개인 주문 · 다른 사유는 무시한다")
    void receivesIndividualOrderPaymentExpired() {
        consumer.handle(relayed(orderCanceled(OrderType.INDIVIDUAL_GROUP, OrderCancelReason.PAYMENT_EXPIRED)));
        consumer.handle(relayed(orderCanceled(OrderType.PERSONAL, OrderCancelReason.PAYMENT_EXPIRED)));
        consumer.handle(relayed(orderCanceled(OrderType.INDIVIDUAL_GROUP, OrderCancelReason.BUYER_CANCELED)));

        then(abuseFacade).should().receive(new AbuseSignalCommand(7L, AbuseSignalType.INDIVIDUAL_ORDER_PAYMENT_EXPIRED,
                "500", CANCELED_AT));
        then(abuseJudgeRunner).should().judge(7L);
        then(abuseFacade).shouldHaveNoMoreInteractions();
        then(abuseJudgeRunner).shouldHaveNoMoreInteractions();
    }

    private OrderCanceledEvent orderCanceled(OrderType orderType, OrderCancelReason reason) {
        return new OrderCanceledEvent(500L, 7L, orderType, reason, false, CANCELED_AT);
    }

    private SharedGroupOrderCanceledEvent canceled(SharedGroupOrderCancelReason reason, List<Long> unpaidMemberIds) {
        return new SharedGroupOrderCanceledEvent(100L, 10L, reason, CANCELED_AT, unpaidMemberIds, CREATOR);
    }

    private AbuseSignalCommand command(Long memberId) {
        return new AbuseSignalCommand(memberId, AbuseSignalType.SHARED_ORDER_PAYMENT_EXPIRED, "100", CANCELED_AT,
                "sharedGroupPurchase:10");
    }

    private OutboxMessageRelayed relayed(DomainEvent event) {
        return new OutboxMessageRelayed(new EventEnvelope("event-id", "test", "test", "SharedGroupOrder", "100",
                CANCELED_AT, 1, "{}"), event);
    }
}
