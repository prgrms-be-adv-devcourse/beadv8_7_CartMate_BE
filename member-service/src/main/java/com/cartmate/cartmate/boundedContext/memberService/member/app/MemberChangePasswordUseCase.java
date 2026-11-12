package com.cartmate.cartmate.boundedContext.memberService.member.app;

import com.cartmate.cartmate.boundedContext.memberService.member.exception.MemberErrorCode;
import com.cartmate.cartmate.boundedContext.memberService.member.domain.Member;
import com.cartmate.cartmate.boundedContext.memberService.member.dto.ChangePasswordRequest;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemberChangePasswordUseCase {
    private final MemberSupport memberSupport;
    private final PasswordEncoder passwordEncoder;
    private final MemberTokenUseCase memberTokenUseCase;
    private final TrustedDeviceUseCase trustedDeviceUseCase;

    public void changePassword(Long memberId, ChangePasswordRequest request) {
        Member member = memberSupport.findById(memberId)
                .orElseThrow(() -> new ServiceException(MemberErrorCode.MEMBER_NOT_FOUND));

        if (!passwordEncoder.matches(request.currentPassword(), member.getPassword())) {
            throw new ServiceException(MemberErrorCode.MEMBER_PASSWORD_MISMATCH);
        }
        if (passwordEncoder.matches(request.newPassword(), member.getPassword())) {
            throw new ServiceException(MemberErrorCode.MEMBER_PASSWORD_SAME);
        }

        member.changePassword(passwordEncoder.encode(request.newPassword()));
        // 비밀번호가 새어 바꾸는 경우를 생각해 로그인 세션(Refresh Token)과 신뢰 기기를 모두 끊는다 → 모든 기기에서 다시 로그인
        memberTokenUseCase.revokeAll(memberId);
        trustedDeviceUseCase.revokeAll(memberId);
    }
}