package com.cartmate.cartmate.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * "다른 스레드가 DB 락을 기다리는 중"을 시간(sleep) 대신 DB 상태로 기다린다.
 *
 * <p>{@code information_schema.innodb_trx}(trx_state = 'LOCK WAIT')나 {@code performance_schema.data_lock_waits}가
 * 가장 정확하지만, 테스트 컨테이너의 DB 계정({@code test})에는 PROCESS 권한 · performance_schema 조회 권한이 없어 읽을 수 없다.
 * 대신 같은 계정의 연결은 {@code information_schema.processlist}로 볼 수 있으므로, 락을 쥔 쪽이 이미 쿼리를 끝내고 쉬는 동안
 * 다른 연결에서 그 테이블을 건드리는 쿼리가 "실행 중"으로 보이면 그 쿼리는 락에 막혀 있는 것으로 본다.
 * 그래서 이 helper는 락을 쥔 트랜잭션이 커밋되지 않고 쉬는 중일 때만 쓴다.
 */
public final class LockWaits {

    public static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final long POLL_MILLIS = 20;

    private static final String RUNNING_SQL = "SELECT CONCAT(id, ':', info) FROM information_schema.processlist "
            + "WHERE id <> CONNECTION_ID() AND command = 'Query' AND info LIKE ?";

    private LockWaits() {
    }

    /**
     * 다른 연결에서 {@code table}을 건드리는 같은 쿼리가 연속 두 번의 확인(20ms 간격)에서 모두 실행 중으로 보일 때까지 기다린다.
     * 한 번만 보이는 것은 막힘 없이 지나가는 짧은 쿼리일 수 있어서, 같은 연결 · 같은 문장이 그대로 남아 있어야 락 대기로 본다.
     * {@code count}개 이상이 그렇게 되면 돌아오고, 시간을 넘기면 실패한다.
     */
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
