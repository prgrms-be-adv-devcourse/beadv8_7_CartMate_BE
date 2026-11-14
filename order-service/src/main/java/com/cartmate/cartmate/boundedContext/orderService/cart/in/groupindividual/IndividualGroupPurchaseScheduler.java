package com.cartmate.cartmate.boundedContext.orderService.cart.in.groupindividual;

import com.cartmate.cartmate.boundedContext.orderService.cart.app.groupindividual.IndividualGroupPurchaseFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cartmate.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class IndividualGroupPurchaseScheduler {

    private static final long INTERVAL_MILLIS = 60_000L;
    private static final String LOCK_AT_MOST = "PT5M";
    private static final String LOCK_AT_LEAST = "PT5S";

    private final IndividualGroupPurchaseFacade individualGroupPurchaseFacade;
    private final Clock clock;

    @Scheduled(fixedDelay = INTERVAL_MILLIS, initialDelay = INTERVAL_MILLIS)
    @SchedulerLock(name = "individualGroupPurchase.closeExpiredDemand", lockAtMostFor = LOCK_AT_MOST, lockAtLeastFor = LOCK_AT_LEAST)
    public void closeExpiredDemand() {
        List<Long> ids = individualGroupPurchaseFacade.findDemandIdsToCloseByDeadline(LocalDateTime.now(clock));
        for (Long id : ids) {
            try {
                individualGroupPurchaseFacade.closeByDeadline(id);
            } catch (Exception e) {
                log.error("어드민 공동구매 마감 시각 종료 실패: id={}", id, e);
            }
        }
    }
}
