package com.cartmate.cartmate.boundedContext.orderService.order.in.groupshared;

import com.cartmate.cartmate.boundedContext.orderService.order.app.groupshared.SharedGroupOrderFacade;
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
public class SharedGroupOrderScheduler {

    private static final long INTERVAL_MILLIS = 60_000L;
    private static final String LOCK_AT_MOST = "PT5M";
    private static final String LOCK_AT_LEAST = "PT5S";

    private final SharedGroupOrderFacade sharedGroupOrderFacade;
    private final Clock clock;

    @Scheduled(fixedDelay = INTERVAL_MILLIS, initialDelay = INTERVAL_MILLIS)
    @SchedulerLock(name = "sharedGroupOrder.cancelExpired", lockAtMostFor = LOCK_AT_MOST, lockAtLeastFor = LOCK_AT_LEAST)
    public void cancelExpired() {
        List<Long> ids = sharedGroupOrderFacade.findIdsToCancelByDeadline(LocalDateTime.now(clock));
        processEach(ids, sharedGroupOrderFacade::cancelByDeadline, "결제 기한 만료 취소");
    }

    private void processEach(List<Long> ids, Consumer<Long> action, String taskName) {
        for (Long id : ids) {
            try {
                action.accept(id);
            } catch (Exception e) {
                log.error("공동 주문 {} 실패: id={}", taskName, id, e);
            }
        }
    }
}
