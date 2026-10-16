package com.cartmate.cartmate.boundedContext.member.domain;

import com.cartmate.cartmate.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "member_suspension")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberSuspension extends BaseIdAndTime {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "reason_type", nullable = false, length = 20)
    private SuspensionReason reasonType;

    @Column(name = "reason_detail", nullable = false, length = 300)
    private String reasonDetail;

    @Column(name = "suspended_at", nullable = false)
    private LocalDateTime suspendedAt;

    @Column(name = "suspended_until")
    private LocalDateTime suspendedUntil;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "release_type", length = 20)
    private SuspensionReleaseType releaseType;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    public static MemberSuspension create(Member member, SuspensionReason reasonType, String reasonDetail,
                                          SuspensionDuration duration, Long adminId, LocalDateTime now) {
        MemberSuspension s = new MemberSuspension();
        s.member = member;
        s.reasonType = reasonType;
        s.reasonDetail = reasonDetail;
        s.suspendedAt = now;
        s.suspendedUntil = duration.getDays() == null ? null : now.plusDays(duration.getDays());
        s.adminId = adminId;
        return s;
    }

    public void release(SuspensionReleaseType type, LocalDateTime now) {
        this.releasedAt = now;
        this.releaseType = type;
    }
}