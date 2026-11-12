package com.cartmate.cartmate.boundedContext.memberService.member.app;

import com.cartmate.cartmate.boundedContext.memberService.member.exception.MemberErrorCode;
import com.cartmate.cartmate.boundedContext.memberService.member.domain.Member;
import com.cartmate.cartmate.boundedContext.memberService.member.dto.LoginOutcome;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class MemberLoginUseCase {
    private final MemberSupport memberSupport;
    private final PasswordEncoder passwordEncoder;
    private final MemberLoginMfaUseCase memberLoginMfaUseCase;

    // 비밀번호까지 확인하고, 2차 인증 여부는 MemberLoginMfaUseCase가 정한다 (비밀번호만 맞으면 토큰을 주지 않는다)
    public LoginOutcome login(String email, String password, boolean remember, String trustedDeviceToken, Instant now) {
        Member member = memberSupport.findByEmail(email.trim())
                .orElseThrow(this::loginFailed);

        if (!passwordEncoder.matches(password, member.getPassword())) {
            throw loginFailed();
        }

        member.validateCanLogin();

        return memberLoginMfaUseCase.afterPassword(member, remember, trustedDeviceToken, now);
    }

    private ServiceException loginFailed() {
        return new ServiceException(MemberErrorCode.MEMBER_LOGIN_FAILED);
    }
}
