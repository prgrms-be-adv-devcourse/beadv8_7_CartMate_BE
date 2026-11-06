package com.cartmate.cartmate.global.outbox;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
            OutboxEventStatus status, LocalDateTime now, Limit limit);

    Optional<OutboxEvent> findByEventId(String eventId);
}
