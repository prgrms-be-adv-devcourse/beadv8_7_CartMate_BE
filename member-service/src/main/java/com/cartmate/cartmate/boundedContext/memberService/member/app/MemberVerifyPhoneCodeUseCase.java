package com.cartmate.cartmate.boundedContext.memberService.member.app;

import com.cartmate.cartmate.boundedContext.memberService.member.domain.MemberPolicy;
import com.cartmate.cartmate.boundedContext.memberService.member.out.PhoneVerificationStore;
import com.cartmate.cartmate.boundedContext.memberService.member.out.VerificationCodeStore.VerifyResult;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class MemberVerifyPhoneCodeUseCase {

    private final PhoneVerificationStore phoneVerificationStore;
    private final MemberPolicy memberPolicy;

    public void verify(String phone, String code) {
        VerifyResult result = phoneVerificationStore.verify(phone, code, Instant.now());
        switch (result.status()) {
            case VERIFIED -> {
            }
            case NOT_SENT -> throw new ServiceException("400-MEMBER_PHONE_CODE_NOT_SENT", "인증번호를 먼저 요청해주세요.");
            case EXPIRED -> throw new ServiceException("400-MEMBER_PHONE_CODE_EXPIRED", "인증번호가 만료되었습니다. 다시 요청해주세요.");
            case TOO_MANY_ATTEMPTS -> throw new ServiceException("429-MEMBER_PHONE_TOO_MANY_ATTEMPTS",
                    "인증 시도 횟수를 초과했습니다. 잠시 후 다시 시도해주세요.");
            case MISMATCH -> throw new ServiceException("400-MEMBER_PHONE_CODE_MISMATCH", "인증번호가 일치하지 않습니다. ("
                    + Math.max(0, memberPolicy.getPhoneMaxAttempts() - result.attempts()) + "번 남음)");
        }
    }
}