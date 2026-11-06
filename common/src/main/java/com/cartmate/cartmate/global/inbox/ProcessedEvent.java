package com.cartmate.cartmate.global.inbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 받는 쪽이 이미 처리한 이벤트 기록({@code processed_event}). 같은 이벤트가 두 번 와도(릴레이 · Kafka는 at-least-once)
 * 한 번만 처리하려고 (받는 쪽 이름, eventId)를 unique로 남긴다.
 * 코드에서는 {@link ProcessedEvents}가 SQL로만 다루고, 이 엔티티는 ddl-auto가 테이블을 만들게 하려고 둔다.
 */
@Entity
@Table(name = "processed_event",
        uniqueConstraints = @UniqueConstraint(name = "uk_processed_event_consumer_event", columnNames = {"consumer", "event_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 받는 쪽 이름. 같은 이벤트를 여러 받는 쪽이 각자 한 번씩 처리하므로 eventId만으로는 부족하다 (예: cart.order-paid)
    @Column(name = "consumer", length = 100, nullable = false)
    private String consumer;

    @Column(name = "event_id", length = 36, nullable = false)
    private String eventId;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;
}
