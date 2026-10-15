package com.cartmate.cartmate.domain.cart.groupshared.scheduler;

import com.cartmate.cartmate.domain.cart.groupshared.repository.SharedGroupPurchaseRepository;
import com.cartmate.cartmate.domain.cart.groupshared.service.SharedGroupPurchaseService;
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

    /*
     * ID마다 처리하고, 하나가 실패해도 다음 ID는 계속 처리한다.
     * - Consumer<Long>: "Long 하나를 받아서 무언가를 하는 함수". 서비스 메서드를 메서드 참조(::)로 넘겨받는다.
     *   두 스케줄 작업의 반복·예외 처리 코드가 같아서 하나로 모았다.
     * - try-catch로 감싸지 않으면 예외 하나가 반복문 전체를 멈춰서, 뒤에 있는 공동구매들이 이번 실행에서 처리되지 않는다.
     *   실패한 공동구매는 상태가 바뀌지 않았으므로 1분 뒤 다음 실행에서 다시 시도된다.
     * - 예외를 삼키는 대신 로그를 남겨서, 같은 공동구매가 계속 실패하면 원인을 찾을 수 있게 한다.
     */
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
