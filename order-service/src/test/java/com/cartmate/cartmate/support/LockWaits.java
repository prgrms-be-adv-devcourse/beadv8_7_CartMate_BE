package com.cartmate.cartmate.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class LockWaits {

    public static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final long POLL_MILLIS = 20;

    private static final String RUNNING_SQL = "SELECT CONCAT(id, ':', info) FROM information_schema.processlist "
            + "WHERE id <> CONNECTION_ID() AND command = 'Query' AND info LIKE ?";

    private LockWaits() {
    }

    public static void awaitBlockedOn(JdbcTemplate jdbcTemplate, String table, int count) throws InterruptedException {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        Set<String> previous = Set.of();
        while (true) {
            Set<String> running = new HashSet<>(jdbcTemplate.queryForList(RUNNING_SQL, String.class, "%" + table + "%"));
            Set<String> stillRunning = new HashSet<>(running);
            stillRunning.retainAll(previous);
            int blocked = stillRunning.size();
            if (blocked >= count) {
                return;
            }
            previous = running;
            if (System.nanoTime() > deadline) {
                throw new AssertionError(table + " 락을 기다리는 쿼리가 " + TIMEOUT.toSeconds() + "초 안에 " + count
                        + "개가 되지 않았다(현재 " + blocked + "개)");
            }
            TimeUnit.MILLISECONDS.sleep(POLL_MILLIS);
        }
    }
}
