package com.cartmate.cartmate.boundedContext.orderService.abuse.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Duration;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "abuse_restriction_history",
        indexes = @Index(name = "idx_abuse_restriction_history_member_started", columnList = "member_id, started_at")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AbuseRestriction {

    public static final Duration RESTRICTION_PERIOD = Duration.ofHours(24);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private Long lastSignalId;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    @Column(nullable = false)
    private LocalDateTime endsAt;

    @Column(nullable = false)
    private int extendedCount;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private AbuseRestriction(Long memberId, Long signalId, LocalDateTime startedAt) {
        this.memberId = memberId;
        this.lastSignalId = signalId;
        this.startedAt = startedAt;
        this.endsAt = startedAt.plus(RESTRICTION_PERIOD);
    }

    public static AbuseRestriction start(Long memberId, Long signalId, LocalDateTime startedAt) {
        return new AbuseRestriction(memberId, signalId, startedAt);
    }

    public boolean extend(Long signalId, LocalDateTime judgedAt) {
        LocalDateTime newEndsAt = judgedAt.plus(RESTRICTION_PERIOD);
        if (!newEndsAt.isAfter(endsAt)) {
            return false;
        }
        this.endsAt = newEndsAt;
        this.lastSignalId = signalId;
        this.extendedCount++;
        return true;
    }
}
