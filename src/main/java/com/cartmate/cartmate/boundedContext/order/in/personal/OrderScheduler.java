package com.cartmate.cartmate.boundedContext.order.in.personal;

import com.cartmate.cartmate.boundedContext.order.app.personal.OrderFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderScheduler {

    private static final long INTERVAL_MILLIS = 60_000L;
    // 여러 인스턴스 중 한 곳만 돈다. 정합성은 행 락 + 상태 재검사가 지키고, 락은 같은 일을 겹쳐 하지 않게 한다.
    // 인스턴스가 죽어도 5분 뒤 풀린다. 5초 최소 유지는 인스턴스 간 시계 차이로 같은 회차가 두 번 도는 것만 막는다
    private static final String LOCK_AT_MOST = "PT5M";
    private static final String LOCK_AT_LEAST = "PT5S";

    private final OrderFacade orderFacade;

    @Scheduled(fixedDelay = INTERVAL_MILLIS, initialDelay = INTERVAL_MILLIS)
    @SchedulerLock(name = "order.cancelExpired", lockAtMostFor = LOCK_AT_MOST, lockAtLeastFor = LOCK_AT_LEAST)
    public void cancelExpired() {
        List<Long> ids = orderFacade.findIdsToCancelByDeadline(LocalDateTime.now());
        processEach(ids, orderFacade::cancelByDeadline, "결제 기한 만료 취소");
    }

    private void processEach(List<Long> ids, Consumer<Long> action, String taskName) {
        for (Long id : ids) {
            try {
                action.accept(id);
            } catch (Exception e) {
                log.error("주문 {} 실패: id={}", taskName, id, e);
            }
        }
    }
}
