package com.cartmate.cartmate.support;

import com.cartmate.cartmate.global.event.DomainEvent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.function.Predicate;

/**
 * {@code ManualOutboxRelayConfig}가 가져오므로 {@link CartMateIntegrationTest}를 단 테스트는 주입만 받으면 된다.
 */
@TestConfiguration
public class TestEventRecorder {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private volatile long baselineId;

    public TestEventRecorder(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public <E extends DomainEvent> long count(Class<E> type, Predicate<E> filter) {
        return jdbcTemplate.queryForList(
                        "SELECT payload FROM outbox_event WHERE event_type = ? AND id > ?",
                        String.class, type.getSimpleName(), baselineId)
                .stream()
                .map(payload -> objectMapper.readValue(payload, type))
                .filter(filter)
                .count();
    }

    public void clear() {
        Long maxId = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) FROM outbox_event", Long.class);
        baselineId = maxId == null ? 0L : maxId;
    }
}
