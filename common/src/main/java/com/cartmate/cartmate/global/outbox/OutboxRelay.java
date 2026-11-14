package com.cartmate.cartmate.global.outbox;

import com.cartmate.cartmate.global.observability.TraceparentSupport;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(name = "cartmate.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    public static final String LOCK_NAME = "outboxRelay";
    static final String SPAN_NAME = "outbox.relay";

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxRelaySink sink;
    private final OutboxRelayWorker worker;
    private final OutboxRelayProperties properties;
    private final TraceparentSupport traceparentSupport;

    @Autowired
    public OutboxRelay(OutboxEventRepository outboxEventRepository, OutboxRelaySink sink, OutboxRelayWorker worker,
                       OutboxRelayProperties properties, TraceparentSupport traceparentSupport) {
        this.outboxEventRepository = outboxEventRepository;
        this.sink = sink;
        this.worker = worker;
        this.properties = properties;
        this.traceparentSupport = traceparentSupport;
    }

    // 추적 없이 직접 만드는 테스트용 (ManualOutboxRelayConfig)
    public OutboxRelay(OutboxEventRepository outboxEventRepository, OutboxRelaySink sink, OutboxRelayWorker worker,
                       OutboxRelayProperties properties) {
        this(outboxEventRepository, sink, worker, properties, TraceparentSupport.noop());
    }

    @Scheduled(
            fixedDelayString = "${custom.outbox.relay.fixed-delay:PT1S}",
            initialDelayString = "${custom.outbox.relay.initial-delay:PT1S}"
    )
    @SchedulerLock(name = LOCK_NAME, lockAtMostFor = "PT30S")
    public void relay() {
        List<OutboxEvent> batch = outboxEventRepository.findByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
                OutboxEventStatus.NEW, LocalDateTime.now(), Limit.of(properties.batchSize()));
        // 행마다 저장 시점의 trace를 부모로 span을 연다 → Kafka 관찰이 헤더로 실어 보내고, 같은 앱 안 전달도 같은 trace.
        // 보낼 행이 없으면 span도 trace도 생기지 않는다 (스케줄 실행 관찰은 꺼 둠)
        for (OutboxEvent event : batch) {
            traceparentSupport.runInSpan(SPAN_NAME, event.getTraceparent(),
                    Map.of("event.type", event.getEventType()), () -> relayOne(event));
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
            int attempts = event.getAttempts() + 1;
            if (failed) {
                log.atError().addKeyValue("event", "outbox.failed")
                        .addKeyValue("eventId", event.getEventId()).addKeyValue("eventType", event.getEventType())
                        .addKeyValue("attempts", attempts).addKeyValue("error", describe(cause))
                        .setCause(cause)
                        .log("아웃박스 릴레이 포기: eventId={} type={} attempts={}",
                                event.getEventId(), event.getEventType(), attempts);
            } else {
                log.atWarn().addKeyValue("event", "outbox.retry")
                        .addKeyValue("eventId", event.getEventId()).addKeyValue("eventType", event.getEventType())
                        .addKeyValue("attempts", attempts)
                        .setCause(cause)
                        .log("아웃박스 릴레이 실패, 다시 시도: eventId={} type={} attempts={}",
                                event.getEventId(), event.getEventType(), attempts);
            }
        } catch (Exception e) {
            log.warn("아웃박스 실패 기록 실패: eventId={} type={}", event.getEventId(), event.getEventType(), e);
        }
    }

    private void giveUp(OutboxEvent event, NonRetryableRelayException cause) {
        try {
            worker.markFailed(event.getId(), describe(cause));
            log.atError().addKeyValue("event", "outbox.failed")
                    .addKeyValue("eventId", event.getEventId()).addKeyValue("eventType", event.getEventType())
                    .addKeyValue("attempts", event.getAttempts() + 1).addKeyValue("error", describe(cause))
                    .setCause(cause)
                    .log("아웃박스 릴레이 포기(다시 시도해도 실패): eventId={} type={}",
                            event.getEventId(), event.getEventType());
        } catch (Exception e) {
            log.warn("아웃박스 실패 기록 실패: eventId={} type={}", event.getEventId(), event.getEventType(), e);
        }
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        return message == null ? e.getClass().getName() : e.getClass().getSimpleName() + ": " + message;
    }
}
