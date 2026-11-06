package com.cartmate.cartmate.global.inbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "inbox_event",
        indexes = @Index(name = "idx_inbox_event_status_id", columnList = "status, id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_inbox_event_topic_event", columnNames = {"topic", "event_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InboxEvent {

    static final int LAST_ERROR_MAX_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "topic", nullable = false, length = 100)
    private String topic;

    @Column(name = "envelope", nullable = false, columnDefinition = "LONGTEXT")
    private String envelope;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "varchar(20)")
    private InboxEventStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_error", length = LAST_ERROR_MAX_LENGTH)
    private String lastError;

    @Column(name = "received_at", nullable = false, updatable = false)
    private LocalDateTime receivedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    void markProcessed(LocalDateTime processedAt) {
        if (this.status != InboxEventStatus.NEW) {
            return;
        }
        this.status = InboxEventStatus.PROCESSED;
        this.processedAt = processedAt;
        this.lastError = null;
    }

    boolean recordFailure(String error, InboxProcessorProperties policy, LocalDateTime now) {
        if (this.status != InboxEventStatus.NEW) {
            return false;
        }
        this.attempts++;
        this.lastError = truncate(error);
        if (this.attempts >= policy.maxAttempts()) {
            this.status = InboxEventStatus.FAILED;
            return true;
        }
        this.nextAttemptAt = now.plus(policy.backoffAfter(this.attempts));
        return false;
    }

    boolean markFailed(String error) {
        if (this.status != InboxEventStatus.NEW) {
            return false;
        }
        this.attempts++;
        this.lastError = truncate(error);
        this.status = InboxEventStatus.FAILED;
        return true;
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= LAST_ERROR_MAX_LENGTH ? error : error.substring(0, LAST_ERROR_MAX_LENGTH);
    }
}
