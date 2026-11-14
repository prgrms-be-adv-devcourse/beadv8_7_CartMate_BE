package com.cartmate.cartmate.boundedContext.orderService.order.in.salesstop;

import com.cartmate.cartmate.boundedContext.orderService.order.app.salesstop.SalesStopCancelFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 배송 서버 응답이 없는 판매글 내림 취소 요청을 다시 보낸다.
 * outbox·Inbox가 재시도를 다 쓰면 FAILED로 남고 되살리지 않으며, 판매글 내림에는 다시 누를 사람이 없어서다.
 * 간격은 구매자 취소 재발행과 같은 설정값을 쓴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cartmate.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SalesStopCancelScheduler {

    private static final long INTERVAL_MILLIS = 60_000L;

    private final SalesStopCancelFacade salesStopCancelFacade;
    private final Clock clock;

    @Value("${custom.order.cancel-resend-after:PT10M}")
    private Duration resendAfter;

    @Scheduled(fixedDelay = INTERVAL_MILLIS, initialDelay = INTERVAL_MILLIS)
    @SchedulerLock(name = "order.resendSalesStopCancelRequest", lockAtMostFor = "PT5M", lockAtLeastFor = "PT5S")
    public void resend() {
        List<Long> ids = salesStopCancelFacade.findIdsToResend(LocalDateTime.now(clock).minus(resendAfter));
        for (Long id : ids) {
            try {
                salesStopCancelFacade.resend(id, resendAfter);
            } catch (Exception e) {
                log.error("판매글 내림 취소 요청 재발행 실패: requestId={}", id, e);
            }
        }
    }
}
