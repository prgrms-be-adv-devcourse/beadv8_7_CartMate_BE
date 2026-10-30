package com.cartmate.cartmate.boundedContext.abuse.in;

import com.cartmate.cartmate.boundedContext.abuse.app.AbuseFacade;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalCommand;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalType;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCanceledEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class AbuseOrderEventListenerTest {

    private static final LocalDateTime CANCELED_AT = LocalDateTime.of(2026, 10, 28, 12, 0);
    private static final SharedGroupOrderCanceledEvent EVENT = new SharedGroupOrderCanceledEvent(
            100L, 10L, "DEADLINE_EXPIRED", CANCELED_AT, List.of(2L, 3L));

    @Mock
    private AbuseFacade abuseFacade;

    @InjectMocks
    private AbuseOrderEventListener listener;

    @Test
    @DisplayName("기한 만료 취소면 미결제 참여자마다 주문 ID를 출처로 한 신호를 접수한다")
    void receivesSignalPerUnpaidMember() {
        listener.receive(EVENT);

        ArgumentCaptor<AbuseSignalCommand> commands = ArgumentCaptor.forClass(AbuseSignalCommand.class);
        then(abuseFacade).should(times(2)).receive(commands.capture());
        assertThat(commands.getAllValues()).containsExactly(
                new AbuseSignalCommand(2L, AbuseSignalType.SHARED_ORDER_PAYMENT_EXPIRED, "100", CANCELED_AT),
                new AbuseSignalCommand(3L, AbuseSignalType.SHARED_ORDER_PAYMENT_EXPIRED, "100", CANCELED_AT));
    }

    @Test
    @DisplayName("기한 만료가 아닌 취소는 신호로 접수하지 않는다")
    void ignoresOtherCancelReasons() {
        listener.receive(new SharedGroupOrderCanceledEvent(100L, 10L, "CREATOR_CANCELED", CANCELED_AT, List.of(2L)));

        then(abuseFacade).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("미결제자 목록이 없으면 접수하지 않는다")
    void receivesNothingWithoutUnpaidMembers() {
        listener.receive(new SharedGroupOrderCanceledEvent(100L, 10L, "DEADLINE_EXPIRED", CANCELED_AT, null));

        then(abuseFacade).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("잘못된 신호는 예외 없이 건너뛰고 나머지 참여자는 접수한다")
    void skipsInvalidSignalWithoutThrowing() {
        SharedGroupOrderCanceledEvent event = new SharedGroupOrderCanceledEvent(
                100L, 10L, "DEADLINE_EXPIRED", CANCELED_AT, Arrays.asList(null, 3L));

        assertThatCode(() -> listener.receive(event)).doesNotThrowAnyException();

        then(abuseFacade).should().receive(
                new AbuseSignalCommand(3L, AbuseSignalType.SHARED_ORDER_PAYMENT_EXPIRED, "100", CANCELED_AT));
        then(abuseFacade).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("커밋 후에는 미결제 참여자마다 판정 Facade를 따로 호출한다")
    void delegatesPerUnpaidMember() {
        listener.handle(EVENT);

        then(abuseFacade).should().handleSharedGroupOrderCanceled(EVENT, 2L);
        then(abuseFacade).should().handleSharedGroupOrderCanceled(EVENT, 3L);
    }

    @Test
    @DisplayName("한 참여자 판정이 실패해도 예외를 던지지 않고 다음 참여자를 처리한다")
    void continuesAfterFailure() {
        willThrow(new IllegalStateException("fail")).given(abuseFacade).handleSharedGroupOrderCanceled(EVENT, 2L);

        assertThatCode(() -> listener.handle(EVENT)).doesNotThrowAnyException();
        then(abuseFacade).should().handleSharedGroupOrderCanceled(EVENT, 3L);
    }

    @Test
    @DisplayName("커밋 후 미결제자 목록이 없으면 아무것도 하지 않는다")
    void noUnpaidMembers() {
        listener.handle(new SharedGroupOrderCanceledEvent(100L, 10L, "DEADLINE_EXPIRED", CANCELED_AT, null));

        then(abuseFacade).shouldHaveNoInteractions();
    }
}
