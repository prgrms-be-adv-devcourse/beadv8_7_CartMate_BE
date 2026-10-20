package com.cartmate.cartmate.boundedContext.member.app;

import com.cartmate.cartmate.boundedContext.member.domain.Member;
import com.cartmate.cartmate.boundedContext.member.dto.LoginResult;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemberLoginUseCase {
    private final MemberSupport memberSupport;
    private final PasswordEncoder passwordEncoder;
    private final MemberTokenUseCase memberTokenUseCase;

    public LoginResult login(String email, String password, boolean remember) {
        Member member = memberSupport.findByEmail(email.trim())
                .orElseThrow(this::loginFailed);

        if (!passwordEncoder.matches(password, member.getPassword())) {
            throw loginFailed();
        }

        member.validateCanLogin();

        return new LoginResult(member.toDto(), memberTokenUseCase.issue(member, remember));
    }

    private ServiceException loginFailed() {
        return new ServiceException("401-MEMBER_LOGIN_FAILED", "이메일 또는 비밀번호가 올바르지 않습니다.");
    }
}