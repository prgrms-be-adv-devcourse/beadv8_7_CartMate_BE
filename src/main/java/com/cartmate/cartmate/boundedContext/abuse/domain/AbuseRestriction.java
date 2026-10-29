package com.cartmate.cartmate.boundedContext.abuse.domain;

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
        name = "abuse_restriction",
        indexes = @Index(name = "idx_abuse_restriction_member_ends_at", columnList = "member_id, ends_at")
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
    private Long judgmentId;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    @Column(nullable = false)
    private LocalDateTime endsAt;

    private LocalDateTime releasedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private AbuseRestriction(Long memberId, Long judgmentId, LocalDateTime startedAt) {
        this.memberId = memberId;
        this.judgmentId = judgmentId;
        this.startedAt = startedAt;
        this.endsAt = startedAt.plus(RESTRICTION_PERIOD);
    }

    public static AbuseRestriction start(Long memberId, Long judgmentId, LocalDateTime startedAt) {
        return new AbuseRestriction(memberId, judgmentId, startedAt);
    }

    public boolean extend(Long judgmentId, LocalDateTime judgedAt) {
        LocalDateTime newEndsAt = judgedAt.plus(RESTRICTION_PERIOD);
        if (!newEndsAt.isAfter(endsAt)) {
            return false;
        }
        this.endsAt = newEndsAt;
        this.judgmentId = judgmentId;
        return true;
    }

    public boolean isActive(LocalDateTime now) {
        return releasedAt == null && now.isBefore(endsAt);
    }
}
