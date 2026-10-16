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

    public void updateProfile(String nickname, String phone) {
        this.nickname = nickname;
        this.phone = phone;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void suspend() {
        if (status == MemberStatus.WITHDRAWN) {
            throw new ServiceException("409-MEMBER_WITHDRAWN", "탈퇴한 회원은 정지할 수 없습니다.");
        }
        if (status == MemberStatus.SUSPENDED) {
            throw new ServiceException("409-MEMBER_ALREADY_SUSPENDED", "이미 정지된 회원입니다.");
        }
        this.status = MemberStatus.SUSPENDED;
    }

    public void activate() {
        if (status != MemberStatus.SUSPENDED) {
            throw new ServiceException("409-MEMBER_NOT_SUSPENDED", "정지 상태가 아닌 회원입니다.");
        }
        this.status = MemberStatus.ACTIVE;
    }

    public void becomeSeller() {
        if (role == MemberRole.SELLER) {
            throw new ServiceException("409-SELLER_ALREADY_REGISTERED", "이미 판매자로 등록된 회원입니다.");
        }
        if (role == MemberRole.ADMIN) {
            throw new ServiceException("400-SELLER_ADMIN_NOT_ALLOWED", "관리자 계정은 판매자로 등록할 수 없습니다.");
        }
        if (status != MemberStatus.ACTIVE) {
            throw new ServiceException("403-MEMBER_NOT_ACTIVE", "정상 상태인 회원만 판매자로 등록할 수 있습니다.");
        }
        if (!MemberPolicy.canRegisterAsSellerStatic(birthDate)) {
            throw new ServiceException("403-SELLER_UNDERAGE", "만 %d세 이상만 판매자로 등록할 수 있습니다.".formatted(MemberPolicy.sellerMinAge()));
        }
        this.role = MemberRole.SELLER;
    }
}
