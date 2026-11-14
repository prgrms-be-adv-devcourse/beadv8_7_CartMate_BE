package com.cartmate.cartmate.global.inbox;

import com.cartmate.cartmate.global.observability.TraceparentSupport;
import com.cartmate.cartmate.global.outbox.NonRetryableRelayException;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
public class InboxProcessor {

    public static final String LOCK_NAME = "inboxProcessor";
    static final String SPAN_NAME = "inbox.process";

    private final InboxEventRepository inboxEventRepository;
    private final InboxWorker worker;
    private final InboxProcessorProperties properties;
    private final TraceparentSupport traceparentSupport;

    public InboxProcessor(InboxEventRepository inboxEventRepository, InboxWorker worker,
                          InboxProcessorProperties properties, TraceparentSupport traceparentSupport) {
        this.inboxEventRepository = inboxEventRepository;
        this.worker = worker;
        this.properties = properties;
        this.traceparentSupport = traceparentSupport;
    }

    @Scheduled(
            fixedDelayString = "${custom.inbox.processor.fixed-delay:PT1S}",
            initialDelayString = "${custom.inbox.processor.initial-delay:PT1S}"
    )
    @SchedulerLock(name = LOCK_NAME, lockAtMostFor = "PT5M")
    public void process() {
        List<InboxEvent> batch = inboxEventRepository.findByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
                InboxEventStatus.NEW, LocalDateTime.now(), Limit.of(properties.batchSize()));
        // 행마다 수신 시점의 trace를 부모로 span을 연다. 처리할 행이 없으면 span도 trace도 생기지 않는다
        for (InboxEvent row : batch) {
            traceparentSupport.runInSpan(SPAN_NAME, row.getTraceparent(),
                    Map.of("event.type", row.getEventType()), () -> processOne(row));
        }
    }

    private void processOne(InboxEvent row) {
        try {
            worker.process(row.getId());
        } catch (NonRetryableRelayException e) {
            giveUp(row, e);
        } catch (Exception e) {
            recordFailure(row, e);
        }
    }

    private void recordFailure(InboxEvent row, Exception cause) {
        try {
            int attempts = row.getAttempts() + 1;
            if (worker.recordFailure(row.getId(), describe(cause))) {
                log.atError().addKeyValue("event", "inbox.failed")
                        .addKeyValue("eventId", row.getEventId()).addKeyValue("eventType", row.getEventType())
                        .addKeyValue("attempts", attempts).addKeyValue("error", describe(cause))
                        .setCause(cause)
                        .log("인박스 처리 포기: eventId={} type={} attempts={}",
                                row.getEventId(), row.getEventType(), attempts);
            } else {
                log.warn("인박스 처리 실패, 다시 시도: eventId={} type={} attempts={}",
                        row.getEventId(), row.getEventType(), attempts, cause);
            }
        } catch (Exception e) {
            log.warn("인박스 실패 기록 실패: eventId={} type={}", row.getEventId(), row.getEventType(), e);
        }
    }

    private void giveUp(InboxEvent row, NonRetryableRelayException cause) {
        try {
            worker.markFailed(row.getId(), describe(cause));
            log.atError().addKeyValue("event", "inbox.failed")
                    .addKeyValue("eventId", row.getEventId()).addKeyValue("eventType", row.getEventType())
                    .addKeyValue("attempts", row.getAttempts() + 1).addKeyValue("error", describe(cause))
                    .setCause(cause)
                    .log("인박스 처리 포기(다시 시도해도 실패): eventId={} type={}", row.getEventId(), row.getEventType());
        } catch (Exception e) {
            log.warn("인박스 실패 기록 실패: eventId={} type={}", row.getEventId(), row.getEventType(), e);
        }
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        return message == null ? e.getClass().getName() : e.getClass().getSimpleName() + ": " + message;
    }
}
