package com.cartmate.cartmate.boundedContext.member.domain;

import com.cartmate.cartmate.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 리프레시 토큰. 원본은 쿠키에만 있고 DB에는 SHA-256 해시만 저장한다.
@Entity
@Table(
        name = "refresh_token",
        uniqueConstraints = @UniqueConstraint(name = "uk_refresh_token_hash", columnNames = "token_hash"),
        indexes = @Index(name = "idx_refresh_token_member", columnList = "member_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseIdAndTime {

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean remember;

    public static RefreshToken issue(Long memberId, String tokenHash, LocalDateTime expiresAt, boolean remember) {
        RefreshToken t = new RefreshToken();
        t.memberId = memberId;
        t.tokenHash = tokenHash;
        t.expiresAt = expiresAt;
        t.remember = remember;
        return t;
    }

    public boolean isExpired(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }
}