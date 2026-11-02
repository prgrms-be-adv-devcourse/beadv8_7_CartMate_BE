package com.cartmate.cartmate.global.outbox;

import com.cartmate.cartmate.shared.event.EventEnvelope;
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

/**
 * 아웃박스 행 = 이벤트 봉투 + 릴레이 상태.
 * 비즈니스 데이터와 같은 트랜잭션에 저장되고, 릴레이가 id 순으로 읽어 싱크로 넘긴다.
 * PUBLISHED 행은 지우지 않는다(보관 정책은 열린 질문).
 */
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

    // 봉투: 소비자가 멱등 처리에 쓰는 키
    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    // 이벤트 클래스 단순 이름 (예: OrderPaidEvent). 역직렬화 · 소비자 분기 키
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

    // 봉투 스키마 버전. 이벤트 필드가 호환되지 않게 바뀔 때 올린다
    @Column(name = "version", nullable = false)
    private int version;

    // 이벤트 record를 Jackson으로 직렬화한 JSON. 주문 항목이 많으면 수십 KB라 LONGTEXT
    @Column(name = "payload", nullable = false, columnDefinition = "LONGTEXT")
    private String payload;

    // 릴레이 상태
    // 다른 엔티티처럼 varchar(20). MySQL 네이티브 enum이면 상태를 늘릴 때 ALTER가 필요하다
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "varchar(20)")
    private OutboxEventStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    // 이 시각이 지나야 릴레이가 다시 집는다. 처음엔 발행 시각, 실패하면 백오프만큼 뒤로 민다
    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_error", length = LAST_ERROR_MAX_LENGTH)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    private OutboxEvent(String eventId, String eventType, String topic, String aggregateType, String aggregateId,
                        LocalDateTime occurredAt, int version, String payload, LocalDateTime createdAt) {
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
    }

    static OutboxEvent create(String eventId, String eventType, String topic, String aggregateType, String aggregateId,
                              LocalDateTime occurredAt, int version, String payload) {
        return new OutboxEvent(eventId, eventType, topic, aggregateType, aggregateId, occurredAt, version, payload,
                occurredAt);
    }

    EventEnvelope toEnvelope() {
        return new EventEnvelope(eventId, eventType, topic, aggregateType, aggregateId, occurredAt, version, payload);
    }

    /**
     * NEW 행만 PUBLISHED로 바꾼다. 락 유지 시간(lockAtMostFor)을 넘겨 다른 노드가 먼저 처리한 행은 건드리지 않는다.
     */
    void markPublished(LocalDateTime publishedAt) {
        if (this.status != OutboxEventStatus.NEW) {
            return;
        }
        this.status = OutboxEventStatus.PUBLISHED;
        this.publishedAt = publishedAt;
        this.lastError = null;
    }

    /**
     * 다시 시도하면 풀릴 수 있는 실패를 기록한다. 다음 시도를 백오프만큼 미루고,
     * 시도 횟수가 최대에 이르면 FAILED로 바꾼다.
     * NEW가 아닌 행(다른 노드가 이미 PUBLISHED로 바꾼 행)은 건드리지 않는다.
     *
     * @return FAILED로 바뀌었으면 true
     */
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

    /**
     * 다시 시도해도 똑같이 실패할 행(모르는 이벤트 타입, 깨진 payload)을 바로 FAILED로 바꾼다.
     *
     * @return FAILED로 바뀌었으면 true
     */
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
