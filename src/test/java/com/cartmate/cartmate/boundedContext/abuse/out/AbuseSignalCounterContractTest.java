package com.cartmate.cartmate.boundedContext.abuse.out;

import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalCommand;
import com.cartmate.cartmate.boundedContext.abuse.domain.AbuseSignalType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

abstract class AbuseSignalCounterContractTest {

    private static final AbuseSignalType TYPE = AbuseSignalType.SHARED_ORDER_PAYMENT_EXPIRED;
    private static final Duration RETENTION = Duration.ofDays(30);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 28, 12, 0);

    private final long member = 1_000_000_000L + ThreadLocalRandom.current().nextLong(1_000_000_000L);

    private AbuseSignalCounter counter;

    protected abstract AbuseSignalCounter createCounter();

    @BeforeEach
    void setUp() {
        counter = createCounter();
    }

    @Test
    @DisplayName("기록이 없는 회원은 0건이다")
    void emptyCount() {
        assertThat(counter.count(member, TYPE, NOW.minus(RETENTION))).isZero();
    }

    @Test
    @DisplayName("기준 시각 이후에 생긴 신호만 센다(기준 시각과 같으면 포함)")
    void countSince() {
        counter.record(signal(member, "1", NOW.minusDays(31)), RETENTION);
        counter.record(signal(member, "2", NOW.minusDays(30)), RETENTION);
        counter.record(signal(member, "3", NOW.minusDays(1)), RETENTION);

        assertThat(counter.count(member, TYPE, NOW.minusDays(30))).isEqualTo(2);
        assertThat(counter.count(member, TYPE, NOW.minusDays(2))).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 중복 방지 키의 신호는 여러 번 기록해도 한 번만 센다")
    void idempotentBySourceKey() {
        boolean first = counter.record(signal(member, "100", NOW), RETENTION);
        boolean second = counter.record(signal(member, "100", NOW.plusMinutes(1)), RETENTION);

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(counter.count(member, TYPE, NOW.minus(RETENTION))).isEqualTo(1);
    }

    @Test
    @DisplayName("회원마다 따로 센다")
    void separatedByMember() {
        long other = member + 1;
        counter.record(signal(member, "100", NOW), RETENTION);
        counter.record(signal(other, "100", NOW), RETENTION);
        counter.record(signal(other, "101", NOW), RETENTION);

        assertThat(counter.count(member, TYPE, NOW.minus(RETENTION))).isEqualTo(1);
        assertThat(counter.count(other, TYPE, NOW.minus(RETENTION))).isEqualTo(2);
    }

    @Test
    @DisplayName("보관 기간이 지난 신호는 새 신호를 기록할 때 정리되어 더 이상 세지 않는다")
    void expiredSignalsAreRemoved() {
        counter.record(signal(member, "1", NOW.minusDays(40)), RETENTION);
        counter.record(signal(member, "2", NOW), RETENTION);

        assertThat(counter.count(member, TYPE, NOW.minusDays(50))).isEqualTo(1);
    }

    @Test
    @DisplayName("여러 스레드가 동시에 기록해도 빠지거나 중복되지 않는다")
    void concurrentRecord() throws InterruptedException {
        int threads = 16;
        int perThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        counter.record(signal(member, "key-" + i, NOW), RETENTION);
                        counter.record(signal(member, "thread-" + Thread.currentThread().threadId() + "-" + i, NOW), RETENTION);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(counter.count(member, TYPE, NOW.minus(RETENTION))).isEqualTo(perThread + (long) threads * perThread);
    }

    private AbuseSignalCommand signal(long memberId, String sourceKey, LocalDateTime occurredAt) {
        return new AbuseSignalCommand(memberId, TYPE, sourceKey, occurredAt);
    }
}
