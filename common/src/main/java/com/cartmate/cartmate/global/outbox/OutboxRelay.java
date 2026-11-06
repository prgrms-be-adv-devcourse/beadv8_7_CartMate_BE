package com.cartmate.cartmate.global.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cartmate.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    public static final String LOCK_NAME = "outboxRelay";

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxRelaySink sink;
    private final OutboxRelayWorker worker;
    private final OutboxRelayProperties properties;

    @Scheduled(
            fixedDelayString = "${custom.outbox.relay.fixed-delay:PT1S}",
            initialDelayString = "${custom.outbox.relay.initial-delay:PT1S}"
    )
    @SchedulerLock(name = LOCK_NAME, lockAtMostFor = "PT30S")
    public void relay() {
        List<OutboxEvent> batch = outboxEventRepository.findByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
                OutboxEventStatus.NEW, LocalDateTime.now(), Limit.of(properties.batchSize()));
        for (OutboxEvent event : batch) {
            relayOne(event);
        }
    }

    private void relayOne(OutboxEvent event) {
        try {
            sink.send(event.toEnvelope());
        } catch (NonRetryableRelayException e) {
            giveUp(event, e);
            return;
        } catch (Exception e) {
            recordFailure(event, e);
            return;
        }
        try {
            worker.markPublished(event.getId(), LocalDateTime.now());
        } catch (Exception e) {
            log.warn("아웃박스 PUBLISHED 기록 실패, 다음 폴링에 다시 전달됨(consumer 멱등 전제): eventId={} type={}",
                    event.getEventId(), event.getEventType(), e);
        }
    }

    private void recordFailure(OutboxEvent event, Exception cause) {
        try {
            boolean failed = worker.recordFailure(event.getId(), describe(cause), LocalDateTime.now());
            if (failed) {
                log.error("아웃박스 릴레이 포기: eventId={} type={} attempts={}",
                        event.getEventId(), event.getEventType(), event.getAttempts() + 1, cause);
            } else {
                log.warn("아웃박스 릴레이 실패, 다시 시도: eventId={} type={} attempts={}",
                        event.getEventId(), event.getEventType(), event.getAttempts() + 1, cause);
            }
        } catch (Exception e) {
            log.warn("아웃박스 실패 기록 실패: eventId={} type={}", event.getEventId(), event.getEventType(), e);
        }
    }

    private void giveUp(OutboxEvent event, NonRetryableRelayException cause) {
        try {
            worker.markFailed(event.getId(), describe(cause));
            log.error("아웃박스 릴레이 포기(다시 시도해도 실패): eventId={} type={}",
                    event.getEventId(), event.getEventType(), cause);
        } catch (Exception e) {
            log.warn("아웃박스 실패 기록 실패: eventId={} type={}", event.getEventId(), event.getEventType(), e);
        }
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        return message == null ? e.getClass().getName() : e.getClass().getSimpleName() + ": " + message;
    }
}
