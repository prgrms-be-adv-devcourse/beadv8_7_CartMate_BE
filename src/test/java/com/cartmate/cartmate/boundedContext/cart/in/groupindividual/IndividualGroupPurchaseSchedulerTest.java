package com.cartmate.cartmate.boundedContext.cart.in.groupindividual;

import com.cartmate.cartmate.boundedContext.cart.app.groupindividual.IndividualGroupPurchaseFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class IndividualGroupPurchaseSchedulerTest {

    @Mock
    private IndividualGroupPurchaseFacade facade;

    @InjectMocks
    private IndividualGroupPurchaseScheduler scheduler;

    @Test
    @DisplayName("마감 시각이 지난 어드민 공동구매를 하나씩 닫고, 하나가 실패해도 나머지는 닫는다")
    void closeEachAndContinueOnFailure() {
        given(facade.findDemandIdsToCloseByDeadline(any())).willReturn(List.of(1L, 2L, 3L));
        willThrow(new IllegalStateException("boom")).given(facade).closeByDeadline(2L);

        scheduler.closeExpiredDemand();

        then(facade).should().closeByDeadline(1L);
        then(facade).should().closeByDeadline(2L);
        then(facade).should().closeByDeadline(3L);
    }
}
