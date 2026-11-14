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

// OTP 백업 코드 (휴대폰 분실 대비). 원문은 등록 때 한 번만 보여 주고, 여기엔 HMAC 해시만. 쓰면 행을 지운다
@Entity
@Table(name = "member_backup_code",
        uniqueConstraints = @UniqueConstraint(name = "uk_member_backup_code", columnNames = {"member_id", "code_hash"}),
        indexes = @Index(name = "idx_member_backup_code_member", columnList = "member_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberBackupCode extends BaseIdAndTime {

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    public static MemberBackupCode of(Long memberId, String codeHash) {
        MemberBackupCode code = new MemberBackupCode();
        code.memberId = memberId;
        code.codeHash = codeHash;
        return code;
    }
}
