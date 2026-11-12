package com.cartmate.cartmate.global.inbox;

import com.cartmate.cartmate.global.event.EventEnvelope;
import com.cartmate.cartmate.global.observability.TraceparentSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class InboxWriter {

    private final JdbcTemplate jdbcTemplate;
    private final TraceparentSupport traceparentSupport;

    public boolean save(String topic, EventEnvelope envelope, String rawEnvelope) {
        LocalDateTime now = LocalDateTime.now();
        try {
            jdbcTemplate.update(
                    "INSERT INTO inbox_event (event_id, event_type, topic, envelope, status, attempts, next_attempt_at, received_at, traceparent) "
                            + "VALUES (?, ?, ?, ?, 'NEW', 0, ?, ?, ?)",
                    envelope.eventId(), envelope.eventType(), topic, rawEnvelope, now, now,
                    traceparentSupport.current());
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }
}
