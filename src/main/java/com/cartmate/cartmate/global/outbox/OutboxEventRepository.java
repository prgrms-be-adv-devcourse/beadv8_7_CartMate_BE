package com.cartmate.cartmate.global.outbox;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    // 인덱스 (status, id)를 탄다. 릴레이가 배치 크기만큼 오래된 순으로 읽는다
    List<OutboxEvent> findByStatusOrderByIdAsc(OutboxEventStatus status, Limit limit);

    Optional<OutboxEvent> findByEventId(String eventId);
}
