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

    private Long lastSignalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20) default 'AUTO'")
    private AbuseRestrictionSource source;

    private LocalDateTime releasedAt;

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

    private AbuseRestriction(Long memberId, Long signalId, AbuseRestrictionSource source, LocalDateTime startedAt,
                             LocalDateTime endsAt) {
        this.memberId = memberId;
        this.lastSignalId = signalId;
        this.source = source;
        this.startedAt = startedAt;
        this.endsAt = endsAt;
    }

    public static AbuseRestriction start(Long memberId, Long signalId, LocalDateTime startedAt) {
        return new AbuseRestriction(memberId, signalId, AbuseRestrictionSource.AUTO, startedAt,
                startedAt.plus(RESTRICTION_PERIOD));
    }

    public static AbuseRestriction startManual(Long memberId, LocalDateTime startedAt, LocalDateTime endsAt) {
        return new AbuseRestriction(memberId, null, AbuseRestrictionSource.MANUAL, startedAt, endsAt);
    }

    public boolean extendTo(LocalDateTime newEndsAt) {
        if (!newEndsAt.isAfter(endsAt)) {
            return false;
        }
        this.endsAt = newEndsAt;
        this.extendedCount++;
        return true;
    }

    public void release(LocalDateTime now) {
        if (endsAt.isAfter(now)) {
            this.endsAt = now;
        }
        this.releasedAt = now;
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
