package com.cartmate.cartmate.global.outbox;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    // 인덱스 (status, id)로 오래된 순으로 훑고, 백오프 중인 행만 건너뛴다(평소엔 거의 없음)
    List<OutboxEvent> findByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
            OutboxEventStatus status, LocalDateTime now, Limit limit);

    Optional<OutboxEvent> findByEventId(String eventId);
}
