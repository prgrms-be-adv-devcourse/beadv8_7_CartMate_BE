package com.cartmate.cartmate.boundedContext.orderService.order.in.personal;

import com.cartmate.cartmate.boundedContext.orderService.order.app.personal.OrderFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PaybackRetrySchedulerTest {

    private final OrderFacade orderFacade = mock(OrderFacade.class);
    private final PaybackRetryScheduler scheduler = new PaybackRetryScheduler(orderFacade);

    @Test
    @DisplayName("한 건의 재요청이 실패해도 나머지 대기 건을 계속 재요청한다")
    void continueAfterFailure() {
        given(orderFacade.findRetryWaitingPaybackIds(anyInt())).willReturn(List.of(1L, 2L));
        willThrow(new IllegalStateException("db down")).given(orderFacade).retryWaitingPayback(1L);

        scheduler.retryWaitingPaybacks();

        verify(orderFacade).retryWaitingPayback(2L);
    }
}
