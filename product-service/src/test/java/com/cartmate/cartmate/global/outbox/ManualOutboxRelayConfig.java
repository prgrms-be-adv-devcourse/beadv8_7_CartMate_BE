package com.cartmate.cartmate.global.outbox;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 테스트는 스케줄링을 끄므로(cartmate.scheduling.enabled=false) OutboxRelay 빈이 없다. 테스트가 직접 relay()를 부를 수 있게 만든다.
 *
 * <p>OutboxRelayWorker가 패키지 안에서만 보여서 global.outbox 패키지에 둔다(order-service와 같은 방식).
 */
@TestConfiguration
public class ManualOutboxRelayConfig {

    // relay()에는 ShedLock이 걸려 있어서, 직접 부르기 전에 잠금을 풀어 둔다
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
