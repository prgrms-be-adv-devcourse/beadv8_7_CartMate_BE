package com.cartmate.cartmate.global.outbox;

import com.cartmate.cartmate.boundedContext.orderService.abuse.out.AbuseMemberStateFaults;
import com.cartmate.cartmate.support.TestEventRecorder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

@TestConfiguration
@Import({TestEventRecorder.class, AbuseMemberStateFaults.class})
public class ManualOutboxRelayConfig {

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
