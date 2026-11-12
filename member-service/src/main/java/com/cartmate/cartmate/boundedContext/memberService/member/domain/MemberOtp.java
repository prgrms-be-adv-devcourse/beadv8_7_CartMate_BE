package com.cartmate.cartmate.boundedContext.memberService.member.domain;

import com.cartmate.cartmate.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 관리자 OTP(TOTP) 등록 정보. 회원당 하나.
 *
 * <ul>
 *   <li>비밀키는 AES-GCM으로 암호화해 저장한다 (DB가 새도 키 없이는 코드를 만들 수 없다)</li>
 *   <li>enabledAt이 비어 있으면 등록 중 (첫 코드를 아직 맞히지 않음) → 로그인에 쓸 수 없다</li>
 *   <li>lastUsedStep: 마지막으로 성공한 30초 구간 번호. 같은 구간 코드를 다시 쓰지 못하게 한다</li>
 * </ul>
 * 등록을 지우는 길은 Break-glass 절차(운영자 2인, 서버에서 실행)뿐이다. 이메일·비밀번호 재설정으로는 지워지지 않는다.
 */
@Entity
@Table(name = "member_otp", uniqueConstraints = @UniqueConstraint(name = "uk_member_otp_member", columnNames = "member_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberOtp extends BaseIdAndTime {

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "secret_encrypted", nullable = false, length = 200)
    private String secretEncrypted;

    @Column(name = "enabled_at")
    private LocalDateTime enabledAt;

    @Column(name = "last_used_step", nullable = false)
    private long lastUsedStep;

    public static MemberOtp pending(Long memberId, String secretEncrypted) {
        MemberOtp otp = new MemberOtp();
        otp.memberId = memberId;
        otp.secretEncrypted = secretEncrypted;
        return otp;
    }

    public void enable(long usedStep, LocalDateTime now) {
        this.enabledAt = now;
        this.lastUsedStep = usedStep;
    }

    public boolean isEnabled() {
        return enabledAt != null;
    }
}
