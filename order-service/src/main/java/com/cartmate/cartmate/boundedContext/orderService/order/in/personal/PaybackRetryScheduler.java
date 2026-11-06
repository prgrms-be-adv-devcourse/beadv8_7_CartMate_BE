package com.cartmate.cartmate.boundedContext.orderService.order.in.personal;

import com.cartmate.cartmate.boundedContext.orderService.order.app.personal.OrderFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cartmate.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class PaybackRetryScheduler {

    static final String LOCK_NAME = "order.retryPayback";
    private static final long INTERVAL_MILLIS = 60_000L;
    private static final int BATCH_SIZE = 100;

    private final OrderFacade orderFacade;

    @Scheduled(fixedDelay = INTERVAL_MILLIS, initialDelay = INTERVAL_MILLIS)
    @SchedulerLock(name = LOCK_NAME, lockAtMostFor = "PT5M", lockAtLeastFor = "PT5S")
    public void retryWaitingPaybacks() {
        for (Long paybackId : orderFacade.findRetryWaitingPaybackIds(BATCH_SIZE)) {
            try {
                orderFacade.retryWaitingPayback(paybackId);
            } catch (RuntimeException e) {
                log.warn("페이백 재요청 실패, 다음 실행에서 다시 시도: paybackId={}", paybackId, e);
            }
        }
    }
}
