package com.cartmate.cartmate.boundedContext.orderService.order.in.personal;

import com.cartmate.cartmate.boundedContext.orderService.order.app.personal.OrderFacade;
import com.cartmate.cartmate.boundedContext.orderService.order.exception.OrderErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.http.InternalApiClient.InternalServerUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class OrderSchedulerTest {

    private final OrderFacade orderFacade = mock(OrderFacade.class);
    private final OrderScheduler scheduler = new OrderScheduler(orderFacade, Clock.systemDefaultZone());

    @Test
    @DisplayName("기한 만료 취소 중 다른 서버가 응답하지 않으면(503) 남은 주문은 이번 회차에 시도하지 않는다 (ISS-27)")
    void stopRoundOnOutage() {
        given(orderFacade.findIdsToCancelByDeadline(any())).willReturn(List.of(1L, 2L, 3L));
        willThrow(new InternalServerUnavailableException("PAYMENT", "결제", null)).given(orderFacade).cancelByDeadline(2L);

        scheduler.cancelExpired();

        then(orderFacade).should().cancelByDeadline(1L);
        then(orderFacade).should(never()).cancelByDeadline(3L);
    }

    @Test
    @DisplayName("한 주문의 결제 정리가 끝나지 않았으면 그 주문만 넘기고 나머지는 계속 취소한다")
    void skipOnlyReleasePending() {
        given(orderFacade.findIdsToCancelByDeadline(any())).willReturn(List.of(1L, 2L, 3L));
        willThrow(new ServiceException(OrderErrorCode.ORDER_PAYMENT_RELEASE_PENDING)).given(orderFacade).cancelByDeadline(2L);

        scheduler.cancelExpired();

        then(orderFacade).should().cancelByDeadline(3L);
    }
}
