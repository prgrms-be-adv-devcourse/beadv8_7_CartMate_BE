package com.cartmate.cartmate.boundedContext.orderService.order.in.personal;

import com.cartmate.cartmate.boundedContext.orderService.order.exception.OrderErrorCode;

import com.cartmate.cartmate.global.exception.ServiceException;

import com.cartmate.cartmate.global.http.InternalApiClient.InternalServerUnavailableException;

import com.cartmate.cartmate.boundedContext.orderService.order.app.personal.OrderFacade;
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
public class OrderScheduler {

    private static final long INTERVAL_MILLIS = 60_000L;
    private static final String LOCK_AT_MOST = "PT5M";
    private static final String LOCK_AT_LEAST = "PT5S";

    private final OrderFacade orderFacade;
    private final Clock clock;

    @Scheduled(fixedDelay = INTERVAL_MILLIS, initialDelay = INTERVAL_MILLIS)
    @SchedulerLock(name = "order.cancelExpired", lockAtMostFor = LOCK_AT_MOST, lockAtLeastFor = LOCK_AT_LEAST)
    public void cancelExpired() {
        List<Long> ids = orderFacade.findIdsToCancelByDeadline(LocalDateTime.now(clock));
        processEach(ids, orderFacade::cancelByDeadline, "결제 기한 만료 취소");
    }

    private void processEach(List<Long> ids, Consumer<Long> action, String taskName) {
        for (Long id : ids) {
            try {
                action.accept(id);
            } catch (InternalServerUnavailableException e) {
                log.warn("주문 {} 중단: 다른 서버 응답 없음 ({}), 다음 주기에 다시 시도. 남은 {}건", taskName, e.getMessage(),
                        ids.size() - ids.indexOf(id));
                return;
            } catch (ServiceException e) {
                if (e.getResultCode().contains(OrderErrorCode.ORDER_PAYMENT_RELEASE_PENDING.getCode())) {
                    log.warn("주문 {} 보류: id={} ({})", taskName, id, e.getMessage());
                } else {
                    log.error("주문 {} 실패: id={}", taskName, id, e);
                }
            } catch (Exception e) {
                log.error("주문 {} 실패: id={}", taskName, id, e);
            }
        }
    }
}
