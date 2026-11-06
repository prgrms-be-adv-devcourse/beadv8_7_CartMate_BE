package com.cartmate.cartmate.boundedContext.orderService.cart.in.groupindividual;

import com.cartmate.cartmate.boundedContext.orderService.cart.app.groupindividual.IndividualGroupPurchaseFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class IndividualGroupPurchaseSchedulerTest {

    private static final Clock CLOCK = Clock.fixed(
            LocalDateTime.of(2026, 10, 19, 12, 0).atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());

    @Mock
    private IndividualGroupPurchaseFacade facade;

    private IndividualGroupPurchaseScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new IndividualGroupPurchaseScheduler(facade, CLOCK);
    }

    @Test
    @DisplayName("마감 시각이 지난 어드민 공동구매를 하나씩 닫고, 하나가 실패해도 나머지는 닫는다")
    void closeEachAndContinueOnFailure() {
        given(facade.findDemandIdsToCloseByDeadline(LocalDateTime.now(CLOCK))).willReturn(List.of(1L, 2L, 3L));
        willThrow(new IllegalStateException("boom")).given(facade).closeByDeadline(2L);

        scheduler.closeExpiredDemand();

        then(facade).should().closeByDeadline(1L);
        then(facade).should().closeByDeadline(2L);
        then(facade).should().closeByDeadline(3L);
    }
}
