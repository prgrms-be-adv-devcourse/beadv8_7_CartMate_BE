package com.cartmate.cartmate.boundedContext.orderService.abuse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Optional;

@Getter
@Entity
@Table(name = "abuse_member_state")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AbuseMemberState {

    @Id
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private AbuseLevel currentLevel;

    private LocalDateTime lastJudgedAt;

    private LocalDateTime restrictedUntil;

    private Long activeRestrictionId;

    private LocalDateTime scoreResetAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    static AbuseMemberState create(Long memberId, LocalDateTime now) {
        AbuseMemberState state = new AbuseMemberState();
        state.memberId = memberId;
        state.currentLevel = AbuseLevel.NONE;
        state.createdAt = now;
        state.updatedAt = now;
        return state;
    }

    public void applyJudgment(AbuseLevel level, LocalDateTime judgedAt) {
        this.currentLevel = isRestricted(judgedAt) ? AbuseLevel.RESTRICTED : level;
        this.lastJudgedAt = judgedAt;
        this.updatedAt = judgedAt;
    }

    public void restrict(Long restrictionId, LocalDateTime endsAt, LocalDateTime now) {
        this.activeRestrictionId = restrictionId;
        this.restrictedUntil = endsAt;
        this.updatedAt = now;
    }

    public void restrictManually(Long restrictionId, LocalDateTime endsAt, LocalDateTime now) {
        restrict(restrictionId, endsAt, now);
        this.currentLevel = AbuseLevel.RESTRICTED;
    }

    public void release(LocalDateTime now) {
        this.activeRestrictionId = null;
        this.restrictedUntil = null;
        this.scoreResetAt = now;
        this.currentLevel = AbuseLevel.NONE;
        this.updatedAt = now;
    }

    public static LocalDateTime scoreWindowStart(LocalDateTime windowStart, LocalDateTime scoreResetAt) {
        return scoreResetAt != null && scoreResetAt.isAfter(windowStart) ? scoreResetAt : windowStart;
    }

    public boolean isRestricted(LocalDateTime now) {
        return restrictedUntil != null && now.isBefore(restrictedUntil);
    }

    public Optional<Long> activeRestrictionId(LocalDateTime now) {
        return isRestricted(now) ? Optional.ofNullable(activeRestrictionId) : Optional.empty();
    }
}
