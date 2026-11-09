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

/**
 * 아웃박스 폴링 릴레이. 시도 시각이 된 NEW 행을 id 순으로 배치만큼 읽어 {@link OutboxRelaySink}에 넘긴다.
 * 성공 → PUBLISHED. 실패 → attempts · last_error를 쌓고 다음 시도를 백오프만큼 미룬다.
 * 최대 시도에 이르거나 다시 해도 소용없는 실패({@link NonRetryableRelayException})면 FAILED + ERROR 로그.
 * FAILED 행은 자동으로 되살리지 않는다. 원인을 고친 뒤 status를 NEW로, next_attempt_at을 지금으로 돌려 다시 태운다.
 *
 * <p>인스턴스가 여럿이어도 한 번에 하나만 돌도록 ShedLock을 건다(이름 {@code outboxRelay}).
 * 락이 있어도 전달은 at-least-once다(싱크 성공 뒤 PUBLISHED 기록이 실패하면 다음 폴링에 다시 전달).
 * consumer가 {@code eventId}로 멱등 처리한다.
 *
 * <p>{@code cartmate.scheduling.enabled=false}면 다른 스케줄러처럼 이 빈도 빠진다(통합 테스트용).
 */
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

    // 한 행의 어떤 실패(싱크 · 상태 기록)도 같은 배치의 다음 행을 막지 않는다. 기록이 실패한 행은 다음 폴링에 다시 온다
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
