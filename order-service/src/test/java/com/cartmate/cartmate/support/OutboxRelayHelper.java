package com.cartmate.cartmate.support;

import com.cartmate.cartmate.global.outbox.ManualOutboxRelayConfig;
import com.cartmate.cartmate.global.outbox.OutboxRelay;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.function.BooleanSupplier;

public final class OutboxRelayHelper {

    public static final int MAX_ATTEMPTS = 20;

    private OutboxRelayHelper() {
    }

    public static void relayOnce(JdbcTemplate jdbcTemplate, OutboxRelay outboxRelay) {
        ManualOutboxRelayConfig.releaseRelayLock(jdbcTemplate);
        outboxRelay.relay();
    }

    public static void relayUntil(JdbcTemplate jdbcTemplate, OutboxRelay outboxRelay, BooleanSupplier condition) {
        relayUntil(jdbcTemplate, outboxRelay, condition, "조건을 만족하지 않음");
    }

    public static void relayUntil(JdbcTemplate jdbcTemplate, OutboxRelay outboxRelay, BooleanSupplier condition,
                                  String description) {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            relayOnce(jdbcTemplate, outboxRelay);
            if (condition.getAsBoolean()) {
                return;
            }
        }
        throw new AssertionError("릴레이를 " + MAX_ATTEMPTS + "번 돌려도 기대한 상태가 되지 않았다: " + description);
    }

    public static void relayUntilPublished(JdbcTemplate jdbcTemplate, OutboxRelay outboxRelay,
                                           String aggregateType, Object aggregateId) {
        relayUntil(jdbcTemplate, outboxRelay, () -> jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM outbox_event WHERE aggregate_type = ? AND aggregate_id = ? AND status = 'NEW'",
                        Integer.class, aggregateType, String.valueOf(aggregateId)) == 0,
                "아웃박스 이벤트가 릴레이되지 않았다: " + aggregateType + "#" + aggregateId);
    }
}
