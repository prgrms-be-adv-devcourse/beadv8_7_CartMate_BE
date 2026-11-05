package com.cartmate.cartmate.global.outbox;

import com.cartmate.cartmate.global.event.DomainEventPublisher;
import com.cartmate.cartmate.global.event.EventEnvelope;
import com.cartmate.cartmate.global.event.OutboxMessageRelayed;
import com.cartmate.cartmate.shared.test.event.OutboxProbeEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 스케줄링은 켜 둔다({@link OutboxRelay}가 ShedLock 프록시를 거쳐 빈으로 떠야 하므로).
 * 대신 첫 폴링을 1시간 뒤로 미뤄 테스트가 직접 {@code relay()}를 부른다.
 */
@SpringBootTest(properties = {
        "custom.outbox.relay.initial-delay=PT1H",
        "custom.outbox.relay.max-attempts=3"
})
@ActiveProfiles("test")
@Import(OutboxIntegrationTest.TestConfig.class)
class OutboxIntegrationTest {

    @Autowired
    private DomainEventPublisher domainEventPublisher;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Recorder recorder;

    @Autowired
    private SwitchableSink switchableSink;

    private final long probeId = 1_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L);

    @BeforeEach
    void setUp() {
        recorder.clear();
        switchableSink.failing.set(false);
        switchableSink.failOnNote = null;
        // 같은 컨테이너 DB를 쓰는 다른 통합 테스트(스케줄링 꺼짐)가 남긴 NEW 행이 배치(100건)를 차지하지 않게
        jdbcTemplate.update("DELETE FROM outbox_event WHERE status = 'NEW'");
        // 다른 컨텍스트가 create-drop으로 shedlock 테이블을 다시 만들면 캐시된 ShedLock이 relay()를 조용히 건너뛴다
        ManualOutboxRelayConfig.releaseRelayLock(jdbcTemplate);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM outbox_event WHERE aggregate_type = 'OutboxProbe'");
    }

    @Test
    @DisplayName("① 발행한 트랜잭션이 롤백되면 outbox 행이 남지 않는다")
    void rollbackLeavesNoRow() {
        transactionTemplate.executeWithoutResult(status -> {
            domainEventPublisher.publish(probe("rollback"));
            status.setRollbackOnly();
        });

        assertThat(rows()).isEmpty();
    }

    @Test
    @DisplayName("② 커밋되면 NEW 행 1건이 남고, 원본 이벤트를 구독하는 기존 리스너도 그대로 불린다(이중 발행)")
    void commitLeavesNewRowAndDispatchesOriginalEvent() {
        OutboxProbeEvent event = probe("commit");

        transactionTemplate.executeWithoutResult(status -> domainEventPublisher.publish(event));

        List<Map<String, Object>> rows = rows();
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.getFirst();
        assertThat(row.get("status")).isEqualTo("NEW");
        assertThat(row.get("event_type")).isEqualTo("OutboxProbeEvent");
        assertThat(row.get("topic")).isEqualTo("cartmate.test.outbox-probe");
        assertThat(row.get("aggregate_id")).isEqualTo(String.valueOf(probeId));
        assertThat(row.get("version")).isEqualTo(1);
        assertThat(row.get("attempts")).isEqualTo(0);
        assertThat(row.get("published_at")).isNull();
        assertThat((String) row.get("event_id")).hasSize(36);
        assertThat((String) row.get("payload")).contains("\"note\":\"commit\"");

        assertThat(recorder.originals).containsExactly(event);
        assertThat(recorder.relayed).isEmpty();
    }

    @Test
    @DisplayName("②-2 payload가 길어도(주문 항목이 많은 이벤트) 잘리지 않고 저장된다")
    void longPayloadIsStoredWhole() {
        String note = "x".repeat(100_000);

        transactionTemplate.executeWithoutResult(status -> domainEventPublisher.publish(probe(note)));

        assertThat((String) rows().getFirst().get("payload")).contains(note);
    }

    @Test
    @DisplayName("③ 릴레이가 NEW → PUBLISHED로 바꾸고 OutboxMessageRelayed를 한 번만 전달한다")
    void relayPublishesOnce() {
        OutboxProbeEvent event = probe("relay");
        transactionTemplate.executeWithoutResult(status -> domainEventPublisher.publish(event));

        outboxRelay.relay();

        Map<String, Object> row = rows().getFirst();
        assertThat(row.get("status")).isEqualTo("PUBLISHED");
        assertThat(row.get("published_at")).isNotNull();
        assertThat(row.get("attempts")).isEqualTo(0);
        assertThat(recorder.relayed).hasSize(1);
        OutboxMessageRelayed relayed = recorder.relayed.getFirst();
        assertThat(relayed.event()).isEqualTo(event);
        assertThat(relayed.is(OutboxProbeEvent.class)).isTrue();
        EventEnvelope envelope = relayed.envelope();
        assertThat(envelope.eventId()).isEqualTo(row.get("event_id"));
        assertThat(envelope.eventType()).isEqualTo("OutboxProbeEvent");
        assertThat(envelope.topic()).isEqualTo("cartmate.test.outbox-probe");
        assertThat(envelope.aggregateType()).isEqualTo("OutboxProbe");
        assertThat(envelope.aggregateId()).isEqualTo(String.valueOf(probeId));
        assertThat(envelope.version()).isEqualTo(1);
        // 원본 리스너는 발행 시점에 한 번만 불렸고 래퍼는 받지 않는다
        assertThat(recorder.originals).containsExactly(event);

        outboxRelay.relay();

        assertThat(recorder.relayed).hasSize(1);
    }

    @Test
    @DisplayName("④ 싱크가 실패하면 다음 시도를 백오프만큼 미룬다: 바로 다시 돌려도 시도하지 않는다")
    void sinkFailureIsRetriedAfterBackoff() {
        transactionTemplate.executeWithoutResult(status -> domainEventPublisher.publish(probe("backoff")));
        switchableSink.failing.set(true);
        LocalDateTime before = LocalDateTime.now();

        outboxRelay.relay();
        outboxRelay.relay();

        Map<String, Object> row = rows().getFirst();
        assertThat(row.get("status")).isEqualTo("NEW");
        assertThat(row.get("attempts")).isEqualTo(1);
        assertThat(((LocalDateTime) row.get("next_attempt_at"))).isAfter(before);

        switchableSink.failing.set(false);
        makeDue();
        outboxRelay.relay();

        row = rows().getFirst();
        assertThat(row.get("status")).isEqualTo("PUBLISHED");
        assertThat(row.get("attempts")).isEqualTo(1);
        assertThat(row.get("last_error")).isNull();
        assertThat(recorder.relayed).hasSize(1);
    }

    @Test
    @DisplayName("④-1 실패가 최대 시도(3회)에 이르면 FAILED가 되어 더는 시도하지 않는다")
    void sinkFailureIsCountedThenGivenUp() {
        transactionTemplate.executeWithoutResult(status -> domainEventPublisher.publish(probe("fail")));
        switchableSink.failing.set(true);

        outboxRelay.relay();
        makeDue();
        outboxRelay.relay();

        Map<String, Object> row = rows().getFirst();
        assertThat(row.get("status")).isEqualTo("NEW");
        assertThat(row.get("attempts")).isEqualTo(2);
        assertThat((String) row.get("last_error")).contains("sink down");

        makeDue();
        outboxRelay.relay();

        row = rows().getFirst();
        assertThat(row.get("status")).isEqualTo("FAILED");
        assertThat(row.get("attempts")).isEqualTo(3);
        assertThat(row.get("published_at")).isNull();

        switchableSink.failing.set(false);
        makeDue();
        outboxRelay.relay();

        assertThat(rows().getFirst().get("attempts")).isEqualTo(3);
        assertThat(recorder.relayed).isEmpty();
    }

    @Test
    @DisplayName("④-3 되돌릴 수 없는 행(모르는 이벤트 타입 · 깨진 payload)은 재시도 없이 바로 FAILED")
    void undecodableRowFailsImmediately() {
        OutboxProbeEvent unknownType = probe("unknown-type");
        OutboxProbeEvent brokenPayload = probe("broken-payload");
        transactionTemplate.executeWithoutResult(status -> {
            domainEventPublisher.publish(unknownType);
            domainEventPublisher.publish(brokenPayload);
        });
        jdbcTemplate.update("UPDATE outbox_event SET event_type = 'NoSuchEvent' WHERE aggregate_id = ? AND payload LIKE ?",
                String.valueOf(probeId), "%unknown-type%");
        jdbcTemplate.update("UPDATE outbox_event SET payload = '{not json' WHERE aggregate_id = ? AND payload LIKE ?",
                String.valueOf(probeId), "%broken-payload%");

        outboxRelay.relay();

        List<Map<String, Object>> rows = rows();
        assertThat(rows).extracting(r -> r.get("status")).containsExactly("FAILED", "FAILED");
        assertThat(rows).extracting(r -> r.get("attempts")).containsExactly(1, 1);
        assertThat(rows).allSatisfy(r -> assertThat((String) r.get("last_error")).contains("NonRetryableRelayException"));
        assertThat(recorder.relayed).isEmpty();
    }

    @Test
    @DisplayName("④-2 한 행의 실패가 같은 배치의 다른 행 전달을 막지 않는다")
    void failureOfOneRowDoesNotBlockOthers() {
        OutboxProbeEvent first = probe("first");
        OutboxProbeEvent second = probe("second");
        transactionTemplate.executeWithoutResult(status -> {
            domainEventPublisher.publish(first);
            domainEventPublisher.publish(second);
        });
        switchableSink.failOnNote = "first";

        outboxRelay.relay();

        List<Map<String, Object>> rows = rows();
        assertThat(rows).extracting(r -> r.get("status")).containsExactly("NEW", "PUBLISHED");
        assertThat(recorder.relayed).extracting(r -> ((OutboxProbeEvent) r.event()).note()).containsExactly("second");
    }

    @Test
    @DisplayName("⑤ 릴레이 메서드에 ShedLock이 걸려 있다: 한 번 돌면 shedlock 테이블에 outboxRelay 락 행이 생긴다")
    void relayIsGuardedByShedLock() {
        outboxRelay.relay();

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shedlock WHERE name = ?", Integer.class, OutboxRelay.LOCK_NAME);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("활성 트랜잭션 없이 발행하면 예외다 (outbox 행과 비즈니스 데이터가 같은 TX여야 하므로)")
    void publishRequiresTransaction() {
        assertThatThrownBy(() -> domainEventPublisher.publish(probe("no-tx")))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private OutboxProbeEvent probe(String note) {
        return new OutboxProbeEvent(probeId, note, LocalDateTime.of(2026, 11, 2, 12, 0, 0, 123_000_000));
    }

    // 백오프를 기다리는 대신 다음 시도 시각을 과거로 당긴다
    private void makeDue() {
        jdbcTemplate.update("UPDATE outbox_event SET next_attempt_at = ? WHERE aggregate_id = ?",
                LocalDateTime.now().minusMinutes(1), String.valueOf(probeId));
    }

    private List<Map<String, Object>> rows() {
        return jdbcTemplate.queryForList(
                "SELECT * FROM outbox_event WHERE aggregate_id = ? ORDER BY id", String.valueOf(probeId));
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        Recorder outboxTestRecorder() {
            return new Recorder();
        }

        @Bean
        @Primary
        SwitchableSink outboxTestSink(InProcessOutboxRelaySink delegate) {
            return new SwitchableSink(delegate);
        }
    }

    /** 원본 이벤트(기존 리스너 역할)와 릴레이 래퍼를 따로 센다. */
    static class Recorder {
        final List<OutboxProbeEvent> originals = new CopyOnWriteArrayList<>();
        final List<OutboxMessageRelayed> relayed = new CopyOnWriteArrayList<>();

        @EventListener
        void onOriginal(OutboxProbeEvent event) {
            originals.add(event);
        }

        @EventListener
        void onRelayed(OutboxMessageRelayed relayed) {
            if (relayed.is(OutboxProbeEvent.class)) {
                this.relayed.add(relayed);
            }
        }

        void clear() {
            originals.clear();
            relayed.clear();
        }
    }

    /** 실제 in-process 싱크를 감싸고, 스위치가 켜지면 실패시킨다. */
    static class SwitchableSink implements OutboxRelaySink {
        final AtomicBoolean failing = new AtomicBoolean(false);
        volatile String failOnNote;
        private final OutboxRelaySink delegate;

        SwitchableSink(OutboxRelaySink delegate) {
            this.delegate = delegate;
        }

        @Override
        public void send(EventEnvelope envelope) {
            if (failing.get() || (failOnNote != null && envelope.payload().contains("\"note\":\"" + failOnNote + "\""))) {
                throw new IllegalStateException("sink down");
            }
            delegate.send(envelope);
        }
    }
}
