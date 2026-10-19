package com.cartmate.cartmate.boundedContext.order.in.groupshared;

import com.cartmate.cartmate.boundedContext.order.app.groupshared.SharedGroupOrderFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class SharedGroupOrderScheduler {

    private static final long INTERVAL_MILLIS = 60_000L;

    private final SharedGroupOrderFacade sharedGroupOrderFacade;

    @Scheduled(fixedDelay = INTERVAL_MILLIS, initialDelay = INTERVAL_MILLIS)
    public void cancelExpired() {
        List<Long> ids = sharedGroupOrderFacade.findIdsToCancelByDeadline(LocalDateTime.now());
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
