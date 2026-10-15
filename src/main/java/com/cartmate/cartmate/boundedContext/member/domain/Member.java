package com.cartmate.cartmate.boundedContext.member.domain;

import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.jpa.entity.BaseIdAndTime;
import com.cartmate.cartmate.shared.member.dto.MemberDto;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseIdAndTime {

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, unique = true, length = 30)
    private String nickname;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private MemberRole role;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private MemberStatus status;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public static Member create(String email, String encodedPassword, String nickname,
                                String phone, LocalDate birthDate) {

        Member member = new Member();
        member.email = email;
        member.password = encodedPassword;
        member.nickname = nickname;
        member.phone = phone;
        member.birthDate = birthDate;
        member.role = MemberRole.BUYER;
        member.status = MemberStatus.ACTIVE;
        return member;
    }

    public MemberDto toDto() {
        return new MemberDto(getId(), email, nickname, role.name());
    }

    public void validateCanLogin() {
        if (status == MemberStatus.SUSPENDED) {
            throw new ServiceException("403-MEMBER_SUSPENDED", "이용이 정지된 계정입니다.");
        }
        if (status == MemberStatus.WITHDRAWN) {
            throw new ServiceException("401-MEMBER_LOGIN_FAILED", "이메일 또는 비밀번호가 올바르지 않습니다.");
        }
    }
}
