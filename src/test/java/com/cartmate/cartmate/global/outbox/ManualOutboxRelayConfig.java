package com.cartmate.cartmate.global.outbox;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 스케줄러를 끈 통합 테스트(cartmate.scheduling.enabled=false)에서 아웃박스 릴레이만 직접 돌리고 싶을 때 쓴다.
 * 스케줄링 전체를 켜면 다른 스케줄러까지 캐시된 컨텍스트에서 계속 돌아 다른 테스트 데이터를 건드린다.
 * 자동 폴링은 함께 쓰는 {@code custom.outbox.relay.initial-delay=PT1H}로 미루고 테스트가 {@link OutboxRelay#relay()}를 부른다.
 */
@TestConfiguration
public class ManualOutboxRelayConfig {

    /**
     * 릴레이를 직접 부르기 전에 ShedLock 락 행({@link OutboxRelay#LOCK_NAME})을 풀린 상태로 만든다.
     * 다른 테스트 컨텍스트가 뜨면서 create-drop으로 shedlock 테이블을 다시 만들면, 캐시돼 있던 이 컨텍스트의 ShedLock은
     * 락 이름을 이미 만들었다고 기억해 UPDATE만 하고 0건이라 "잠김"으로 보고 {@code relay()}를 조용히 건너뛴다.
     * 행을 지우지 않고 있게 한 뒤 만료시킨다(CartOrderSchedulerLockTest와 같은 방식).
     */
    public static void releaseRelayLock(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("INSERT INTO shedlock (name, lock_until, locked_at, locked_by) "
                + "VALUES (?, UTC_TIMESTAMP(3) - INTERVAL 1 MINUTE, UTC_TIMESTAMP(3) - INTERVAL 1 MINUTE, 'test-reset') "
                + "ON DUPLICATE KEY UPDATE lock_until = VALUES(lock_until), locked_at = VALUES(locked_at), "
                + "locked_by = VALUES(locked_by)", OutboxRelay.LOCK_NAME);
    }

    @Bean
    OutboxRelay outboxRelay(OutboxEventRepository outboxEventRepository, OutboxRelaySink sink,
                            OutboxRelayWorker worker, OutboxRelayProperties properties) {
        return new OutboxRelay(outboxEventRepository, sink, worker, properties);
    }
}
