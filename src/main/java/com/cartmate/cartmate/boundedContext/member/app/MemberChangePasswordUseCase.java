package com.cartmate.cartmate.boundedContext.member.app;

import com.cartmate.cartmate.boundedContext.member.domain.Member;
import com.cartmate.cartmate.boundedContext.member.dto.ChangePasswordRequest;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemberChangePasswordUseCase {
    private final MemberSupport memberSupport;
    private final PasswordEncoder passwordEncoder;

    public void changePassword(Long memberId, ChangePasswordRequest request) {
        Member member = memberSupport.findById(memberId)
                .orElseThrow(() -> new ServiceException("404-MEMBER_NOT_FOUND", "회원을 찾을 수 없습니다."));

        if (!passwordEncoder.matches(request.currentPassword(), member.getPassword())) {
            throw new ServiceException("400-MEMBER_PASSWORD_MISMATCH", "현재 비밀번호가 올바르지 않습니다.");
        }
        if (passwordEncoder.matches(request.newPassword(), member.getPassword())) {
            throw new ServiceException("400-MEMBER_PASSWORD_SAME", "새 비밀번호가 현재 비밀번호와 같습니다.");
        }

        member.changePassword(passwordEncoder.encode(request.newPassword()));
    }
}