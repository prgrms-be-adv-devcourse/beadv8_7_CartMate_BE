package com.cartmate.cartmate.boundedContext.productService.payback.in;

import com.cartmate.cartmate.boundedContext.productService.payback.app.PaybackFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PaybackRetrySchedulerTest {

    private final PaybackFacade paybackFacade = mock(PaybackFacade.class);
    private final PaybackRetryScheduler scheduler = new PaybackRetryScheduler(paybackFacade);

    @Test
    @DisplayName("한 건의 재요청이 실패해도 나머지 대기 건을 계속 재요청한다")
    void continueAfterFailure() {
        given(paybackFacade.findRetryWaitingPaybackIds(anyInt())).willReturn(List.of(1L, 2L));
        willThrow(new IllegalStateException("db down")).given(paybackFacade).retryWaitingPayback(1L);

        scheduler.retryWaitingPaybacks();

        verify(paybackFacade).retryWaitingPayback(2L);
    }
}
