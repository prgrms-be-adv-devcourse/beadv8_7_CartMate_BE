package com.cartmate.cartmate.support;

import com.cartmate.cartmate.global.event.DomainEvent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.function.Predicate;

/**
 * 커밋된 도메인 이벤트를 센다(ARCH-18). 테스트마다 따로 두던 이벤트 기록기(@Import로 넣으면 컨텍스트가 갈라짐)를 하나로 합쳤다.
 * {@code ManualOutboxRelayConfig}가 가져오므로 {@link CartMateIntegrationTest}를 단 테스트는 주입만 받으면 된다.
 *
 * <p>이벤트는 outbox 행으로만 나간다(이중 발행 제거, MSA-5). 그래서 원본 이벤트를 듣지 않고 커밋된 {@code outbox_event} 행을 읽어 센다.
 * 커밋된 행만 보이므로 "커밋된 이벤트만 센다"는 뜻은 그대로다. 릴레이를 돌리지 않아도 셀 수 있다.
 * 컨텍스트를 여러 테스트가 같이 쓰므로 테스트 시작 때 {@link #clear()}하고(그 뒤 행만 셈), 셀 때는 자기 데이터의 ID로 거른다.
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

    // 지금까지 쌓인 행은 세지 않는다
    public void clear() {
        Long maxId = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) FROM outbox_event", Long.class);
        baselineId = maxId == null ? 0L : maxId;
    }
}
