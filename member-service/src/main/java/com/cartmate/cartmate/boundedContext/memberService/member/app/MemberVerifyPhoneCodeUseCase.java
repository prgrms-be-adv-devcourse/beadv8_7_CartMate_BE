package com.cartmate.cartmate.boundedContext.memberService.member.app;

import com.cartmate.cartmate.boundedContext.memberService.member.exception.MemberErrorCode;
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
            case NOT_SENT -> throw new ServiceException(MemberErrorCode.MEMBER_PHONE_CODE_NOT_SENT);
            case EXPIRED -> throw new ServiceException(MemberErrorCode.MEMBER_PHONE_CODE_EXPIRED);
            case TOO_MANY_ATTEMPTS -> throw new ServiceException(MemberErrorCode.MEMBER_PHONE_TOO_MANY_ATTEMPTS);
            case MISMATCH -> throw new ServiceException(MemberErrorCode.MEMBER_PHONE_CODE_MISMATCH.resultCode(), "인증번호가 일치하지 않습니다. ("
                    + Math.max(0, memberPolicy.getPhoneMaxAttempts() - result.attempts()) + "번 남음)");
        }
    }
}