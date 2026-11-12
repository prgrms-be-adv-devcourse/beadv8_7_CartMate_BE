package com.cartmate.cartmate.global.outbox;

import com.cartmate.cartmate.global.event.EventEnvelope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_event", indexes = @Index(name = "idx_outbox_event_status_id", columnList = "status, id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    public static final int SCHEMA_VERSION = 1;
    static final int LAST_ERROR_MAX_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "topic", nullable = false, length = 100)
    private String topic;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "payload", nullable = false, columnDefinition = "LONGTEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "varchar(20)")
    private OutboxEventStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_error", length = LAST_ERROR_MAX_LENGTH)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    // 저장 시점의 W3C traceparent. 릴레이가 이 값을 부모로 보내 traceId가 이어진다. 없으면 NULL (예전 행 · 추적 꺼짐)
    @Column(name = "traceparent", length = 55)
    private String traceparent;

    private OutboxEvent(String eventId, String eventType, String topic, String aggregateType, String aggregateId,
                        LocalDateTime occurredAt, int version, String payload, LocalDateTime createdAt,
                        String traceparent) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.topic = topic;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.occurredAt = occurredAt;
        this.version = version;
        this.payload = payload;
        this.status = OutboxEventStatus.NEW;
        this.attempts = 0;
        this.nextAttemptAt = createdAt;
        this.createdAt = createdAt;
        this.traceparent = traceparent;
    }

    static OutboxEvent create(String eventId, String eventType, String topic, String aggregateType, String aggregateId,
                              LocalDateTime occurredAt, int version, String payload, String traceparent) {
        return new OutboxEvent(eventId, eventType, topic, aggregateType, aggregateId, occurredAt, version, payload,
                occurredAt, traceparent);
    }

    EventEnvelope toEnvelope() {
        return new EventEnvelope(eventId, eventType, topic, aggregateType, aggregateId, occurredAt, version, payload);
    }

    void markPublished(LocalDateTime publishedAt) {
        if (this.status != OutboxEventStatus.NEW) {
            return;
        }
        this.status = OutboxEventStatus.PUBLISHED;
        this.publishedAt = publishedAt;
        this.lastError = null;
    }

    boolean recordFailure(String error, OutboxRelayProperties policy, LocalDateTime now) {
        if (this.status != OutboxEventStatus.NEW) {
            return false;
        }
        this.attempts++;
        this.lastError = truncate(error);
        if (this.attempts >= policy.maxAttempts()) {
            this.status = OutboxEventStatus.FAILED;
            return true;
        }
        this.nextAttemptAt = now.plus(policy.backoffAfter(this.attempts));
        return false;
    }

    boolean markFailed(String error) {
        if (this.status != OutboxEventStatus.NEW) {
            return false;
        }
        this.attempts++;
        this.lastError = truncate(error);
        this.status = OutboxEventStatus.FAILED;
        return true;
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= LAST_ERROR_MAX_LENGTH ? error : error.substring(0, LAST_ERROR_MAX_LENGTH);
    }
}
