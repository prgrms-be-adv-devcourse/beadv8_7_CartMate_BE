package com.cartmate.cartmate.boundedContext.memberService.member.domain;

import com.cartmate.cartmate.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 2차 인증을 생략하는 기기 ("이 기기 30일 기억").
 *
 * <p>기기 토큰은 쿠키에만 있고 여기엔 SHA-256 해시만 둔다. 로그인에 쓸 때마다 새 토큰으로 바꾼다
 * → 복사해 간 예전 토큰은 다음 사용부터 못 쓴다. 해제하면 행을 지우고, 만료된 행은 매일 정리한다.
 */
@Entity
@Table(name = "trusted_device",
        uniqueConstraints = @UniqueConstraint(name = "uk_trusted_device_token_hash", columnNames = "token_hash"),
        indexes = @Index(name = "idx_trusted_device_member", columnList = "member_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrustedDevice extends BaseIdAndTime {

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    // 화면에 보일 기기 이름 (브라우저 User-Agent). 기기를 증명하는 값은 아니다
    @Column(name = "device_name", length = 200)
    private String deviceName;

    @Column(name = "last_used_at", nullable = false)
    private LocalDateTime lastUsedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    public static TrustedDevice register(Long memberId, String tokenHash, String deviceName,
                                         LocalDateTime now, LocalDateTime expiresAt) {
        TrustedDevice device = new TrustedDevice();
        device.memberId = memberId;
        device.tokenHash = tokenHash;
        device.deviceName = deviceName;
        device.lastUsedAt = now;
        device.expiresAt = expiresAt;
        return device;
    }
}
