package com.cartmate.cartmate.support;

import com.cartmate.cartmate.global.outbox.ManualOutboxRelayConfig;
import com.cartmate.cartmate.global.outbox.OutboxRelay;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.function.BooleanSupplier;

/**
 * 통합 테스트에서 아웃박스 릴레이를 손으로 돌리는 helper. 릴레이 락을 풀고({@link ManualOutboxRelayConfig#releaseRelayLock})
 * {@link OutboxRelay#relay()}를 부른다. 상태가 없어 빈으로 두지 않는다(공용 컨텍스트 구성을 바꾸지 않음).
 */
public final class OutboxRelayHelper {

    public static final int MAX_ATTEMPTS = 20;

    private OutboxRelayHelper() {
    }

    /** 릴레이를 한 번 돌린다. */
    public static void relayOnce(JdbcTemplate jdbcTemplate, OutboxRelay outboxRelay) {
        ManualOutboxRelayConfig.releaseRelayLock(jdbcTemplate);
        outboxRelay.relay();
    }

    /** 조건이 맞을 때까지 릴레이를 돌린다. {@link #MAX_ATTEMPTS}번 돌려도 맞지 않으면 실패한다. */
    public static void relayUntil(JdbcTemplate jdbcTemplate, OutboxRelay outboxRelay, BooleanSupplier condition) {
        relayUntil(jdbcTemplate, outboxRelay, condition, "조건을 만족하지 않음");
    }

    /** 조건이 맞을 때까지 릴레이를 돌린다. {@link #MAX_ATTEMPTS}번 돌려도 맞지 않으면 {@code description}을 담아 실패한다. */
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

    /** 그 애그리거트의 아웃박스 행이 모두 넘어갈(NEW가 없을) 때까지 릴레이를 돌린다. */
    public static void relayUntilPublished(JdbcTemplate jdbcTemplate, OutboxRelay outboxRelay,
                                           String aggregateType, Object aggregateId) {
        relayUntil(jdbcTemplate, outboxRelay, () -> jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM outbox_event WHERE aggregate_type = ? AND aggregate_id = ? AND status = 'NEW'",
                        Integer.class, aggregateType, String.valueOf(aggregateId)) == 0,
                "아웃박스 이벤트가 릴레이되지 않았다: " + aggregateType + "#" + aggregateId);
    }
}
