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

@Entity
@Table(name = "processed_event",
        uniqueConstraints = @UniqueConstraint(name = "uk_processed_event_consumer_event", columnNames = {"consumer", "event_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "consumer", length = 100, nullable = false)
    private String consumer;

    @Column(name = "event_id", length = 36, nullable = false)
    private String eventId;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;
}
