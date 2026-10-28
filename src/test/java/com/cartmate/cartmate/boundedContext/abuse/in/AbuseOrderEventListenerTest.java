package com.cartmate.cartmate.boundedContext.abuse.in;

import com.cartmate.cartmate.boundedContext.abuse.app.AbuseFacade;
import com.cartmate.cartmate.shared.order.event.SharedGroupOrderCanceledEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class AbuseOrderEventListenerTest {

    private static final SharedGroupOrderCanceledEvent EVENT = new SharedGroupOrderCanceledEvent(
            100L, 10L, "DEADLINE_EXPIRED", LocalDateTime.now(), List.of(2L, 3L));

    @Mock
    private AbuseFacade abuseFacade;

    @InjectMocks
    private AbuseOrderEventListener listener;

    @Test
    @DisplayName("미결제 참여자마다 Facade를 따로 호출한다")
    void delegatesPerUnpaidMember() {
        listener.handle(EVENT);

        then(abuseFacade).should().handleSharedGroupOrderCanceled(EVENT, 2L);
        then(abuseFacade).should().handleSharedGroupOrderCanceled(EVENT, 3L);
    }

    @Test
    @DisplayName("한 참여자 처리가 실패해도 예외를 던지지 않고 다음 참여자를 처리한다")
    void continuesAfterFailure() {
        willThrow(new IllegalStateException("fail")).given(abuseFacade).handleSharedGroupOrderCanceled(EVENT, 2L);

        assertThatCode(() -> listener.handle(EVENT)).doesNotThrowAnyException();
        then(abuseFacade).should().handleSharedGroupOrderCanceled(EVENT, 3L);
    }

    @Test
    @DisplayName("미결제자 목록이 없으면 아무것도 하지 않는다")
    void noUnpaidMembers() {
        listener.handle(new SharedGroupOrderCanceledEvent(100L, 10L, "DEADLINE_EXPIRED", LocalDateTime.now(), null));

        then(abuseFacade).shouldHaveNoInteractions();
    }
}
