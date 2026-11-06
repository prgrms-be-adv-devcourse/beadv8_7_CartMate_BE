package com.cartmate.cartmate.boundedContext.orderService.cart.in.groupshared;

import com.cartmate.cartmate.boundedContext.orderService.cart.app.groupshared.SharedGroupPurchaseFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class SharedGroupPurchaseScheduler {

    private static final long INTERVAL_MILLIS = 60_000L;
    private static final String LOCK_AT_MOST = "PT5M";
    private static final String LOCK_AT_LEAST = "PT5S";

    private final SharedGroupPurchaseFacade sharedGroupPurchaseFacade;
    private final Clock clock;

    @Scheduled(fixedDelay = INTERVAL_MILLIS, initialDelay = INTERVAL_MILLIS)
    @SchedulerLock(name = "sharedGroupPurchase.closeExpired", lockAtMostFor = LOCK_AT_MOST, lockAtLeastFor = LOCK_AT_LEAST)
    public void closeExpired() {
        List<Long> ids = sharedGroupPurchaseFacade.findIdsToCloseByDeadline(LocalDateTime.now(clock));
        processEach(ids, sharedGroupPurchaseFacade::closeByDeadline, "자동 마감");
    }

    @Scheduled(fixedDelay = INTERVAL_MILLIS, initialDelay = INTERVAL_MILLIS)
    @SchedulerLock(name = "sharedGroupPurchase.closeFrozenTimeout", lockAtMostFor = LOCK_AT_MOST, lockAtLeastFor = LOCK_AT_LEAST)
    public void closeFrozenTimeout() {
        List<Long> ids = sharedGroupPurchaseFacade.findIdsToReleaseFrozen(LocalDateTime.now(clock));
        processEach(ids, sharedGroupPurchaseFacade::closeByFrozenTimeout, "마감 보류 해제");
    }

    @Scheduled(fixedDelay = INTERVAL_MILLIS, initialDelay = INTERVAL_MILLIS)
    @SchedulerLock(name = "sharedGroupPurchase.revertOrderTimeout", lockAtMostFor = LOCK_AT_MOST, lockAtLeastFor = LOCK_AT_LEAST)
    public void revertOrderTimeout() {
        List<Long> ids = sharedGroupPurchaseFacade.findIdsAwaitingOrderTooLong(LocalDateTime.now(clock));
        processEach(ids, sharedGroupPurchaseFacade::revertByOrderCreationTimeout, "주문 생성 시간 초과 복귀");
    }

    private void processEach(List<Long> ids, Consumer<Long> action, String taskName) {
        for (Long id : ids) {
            try {
                action.accept(id);
            } catch (Exception e) {
                log.error("공동구매 {} 실패: id={}", taskName, id, e);
            }
        }
    }
}
