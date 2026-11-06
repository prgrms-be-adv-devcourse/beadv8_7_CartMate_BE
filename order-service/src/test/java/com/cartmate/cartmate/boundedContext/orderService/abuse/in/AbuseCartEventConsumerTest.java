package com.cartmate.cartmate.boundedContext.orderService.abuse.in;

import com.cartmate.cartmate.boundedContext.orderService.abuse.app.AbuseFacade;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignalCommand;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseSignalType;
import com.cartmate.cartmate.global.event.DomainEvent;
import com.cartmate.cartmate.global.event.EventEnvelope;
import com.cartmate.cartmate.global.event.OutboxMessageRelayed;
import com.cartmate.cartmate.shared.cart.SharedGroupPurchaseCancelReason;
import com.cartmate.cartmate.shared.cart.event.SharedGroupPurchaseCanceledEvent;
import com.cartmate.cartmate.shared.cart.event.SharedGroupPurchaseClosedEvent;
import com.cartmate.cartmate.shared.cart.event.SharedGroupPurchaseMemberJoinedEvent;
import com.cartmate.cartmate.shared.cart.event.SharedGroupPurchaseMemberLeftEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AbuseCartEventConsumerTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 6, 12, 0);

    @Mock
    private AbuseFacade abuseFacade;

    @Mock
    private AbuseJudgeRunner abuseJudgeRunner;

    private AbuseCartEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new AbuseCartEventConsumer(new AbuseSignalIntake(abuseFacade, abuseJudgeRunner));
    }

    @Test
    @DisplayName("진행 중인 참여가 기준(5개) 이상일 때 참여하면 동시 다중 참여 신호를 접수하고, 기준 미만이면 무시한다")
    void concurrentJoinOnlyAtThreshold() {
        consumer.handle(relayed(new SharedGroupPurchaseMemberJoinedEvent(10L, 2L, 4, AT)));
        consumer.handle(relayed(new SharedGroupPurchaseMemberJoinedEvent(11L, 2L, 5, AT)));

        then(abuseFacade).should().receive(new AbuseSignalCommand(2L, AbuseSignalType.SHARED_PURCHASE_CONCURRENT_JOIN,
                "11:" + AT, AT, "sharedGroupPurchase:11"));
        then(abuseJudgeRunner).should().judge(2L);
        then(abuseFacade).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("스스로 이탈하면 이탈 시각을 출처 키에 넣어 이탈 신호를 접수한다(같은 공동구매를 다시 들어왔다 나가도 따로 센다)")
    void receivesLeftSignal() {
        consumer.handle(relayed(new SharedGroupPurchaseMemberLeftEvent(10L, 2L, AT.minusHours(1), AT)));

        then(abuseFacade).should().receive(new AbuseSignalCommand(2L, AbuseSignalType.SHARED_PURCHASE_LEFT,
                "10:" + AT, AT, "sharedGroupPurchase:10"));
        then(abuseJudgeRunner).should().judge(2L);
    }

    @Test
    @DisplayName("이탈 시각이 없는 이탈 이벤트는 잘못된 신호라 접수하지 않는다")
    void skipsLeftWithoutTime() {
        consumer.accept(new SharedGroupPurchaseMemberLeftEvent(10L, 2L, null, null));

        then(abuseFacade).shouldHaveNoInteractions();
        then(abuseJudgeRunner).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("방장이 공동구매를 취소하면 방장 취소 신호를 접수하고, 다른 취소 사유는 무시한다")
    void receivesCreatorCanceledOnly() {
        consumer.handle(relayed(canceled(SharedGroupPurchaseCancelReason.CREATOR_CANCELED)));
        consumer.handle(relayed(canceled(SharedGroupPurchaseCancelReason.NO_ITEMS_AT_DEADLINE)));
        consumer.handle(relayed(canceled(SharedGroupPurchaseCancelReason.SALES_STOPPED)));

        then(abuseFacade).should().receive(new AbuseSignalCommand(1L, AbuseSignalType.SHARED_PURCHASE_CREATOR_CANCELED,
                "purchase:10", AT, "sharedGroupPurchase:10"));
        then(abuseJudgeRunner).should().judge(1L);
        then(abuseFacade).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("일시 장애로 판정이 멈춰도 접수는 끝난 상태라 예외를 던지지 않는다")
    void transientJudgeFailureDoesNotThrow() {
        given(abuseJudgeRunner.judge(2L)).willReturn(AbuseJudgeOutcome.TRANSIENT_FAILURE);

        consumer.accept(new SharedGroupPurchaseMemberLeftEvent(10L, 2L, AT.minusHours(1), AT));

        then(abuseFacade).should().receive(any());
        then(abuseFacade).should(never()).recordJudgeFailure(any(), any());
    }

    @Test
    @DisplayName("어뷰징이 받지 않는 이벤트는 아무 처리도 하지 않는다")
    void ignoresOtherEvents() {
        consumer.handle(relayed(new SharedGroupPurchaseClosedEvent(10L, 1L, List.of(1L, 2L), List.of(), AT)));

        then(abuseFacade).shouldHaveNoInteractions();
        then(abuseJudgeRunner).shouldHaveNoInteractions();
    }

    private SharedGroupPurchaseCanceledEvent canceled(SharedGroupPurchaseCancelReason reason) {
        return new SharedGroupPurchaseCanceledEvent(10L, 1L, List.of(1L, 2L), reason, AT);
    }

    private OutboxMessageRelayed relayed(DomainEvent event) {
        return new OutboxMessageRelayed(new EventEnvelope("event-id", event.getClass().getSimpleName(), "test",
                event.aggregateType(), event.aggregateId(), AT, 1, "{}"), event);
    }
}
