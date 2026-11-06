package com.cartmate.cartmate.global.inbox;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class ProcessedEvents {

    private final JdbcTemplate jdbcTemplate;

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean markFirst(String consumer, String eventId) {
        int inserted = jdbcTemplate.update(
                "INSERT IGNORE INTO processed_event (consumer, event_id, processed_at) VALUES (?, ?, ?)",
                consumer, eventId, LocalDateTime.now());
        return inserted == 1;
    }
}
