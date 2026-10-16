package com.cartmate.cartmate.boundedContext.cart.in.groupshared;

import com.cartmate.cartmate.boundedContext.cart.out.groupshared.SharedGroupPurchaseRepository;
import com.cartmate.cartmate.boundedContext.cart.app.groupshared.SharedGroupPurchaseService;
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
public class SharedGroupPurchaseScheduler {


    private static final long INTERVAL_MILLIS = 60_000L;

    private final SharedGroupPurchaseRepository sharedGroupPurchaseRepository;
    private final SharedGroupPurchaseService sharedGroupPurchaseService;


    @Scheduled(fixedDelay = INTERVAL_MILLIS, initialDelay = INTERVAL_MILLIS)
    public void closeExpired() {
        List<Long> ids = sharedGroupPurchaseRepository.findIdsToCloseByDeadline(LocalDateTime.now());
        processEach(ids, sharedGroupPurchaseService::closeByDeadline, "자동 마감");
    }

    @Scheduled(fixedDelay = INTERVAL_MILLIS, initialDelay = INTERVAL_MILLIS)
    public void closeFrozenTimeout() {
        List<Long> ids = sharedGroupPurchaseRepository.findIdsToReleaseFrozen(LocalDateTime.now());
        processEach(ids, sharedGroupPurchaseService::closeByFrozenTimeout, "마감 보류 해제");
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
