package com.cartmate.cartmate.boundedContext.member.app;

import com.cartmate.cartmate.boundedContext.member.domain.MemberPolicy;
import com.cartmate.cartmate.boundedContext.member.out.PhoneVerificationStore;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MemberVerifyPhoneCodeUseCase {
    private final PhoneVerificationStore phoneVerificationStore;
    private final MemberPolicy memberPolicy;

    public void verify(String phone, String code) {
        PhoneVerificationStore.Entry entry = phoneVerificationStore.find(phone)
                .orElseThrow(() -> new ServiceException("400-MEMBER_PHONE_CODE_NOT_SENT", "인증번호를 먼저 요청해주세요."));

        LocalDateTime now = LocalDateTime.now();

        if (entry.getCodeExpiresAt().isBefore(now)) {
            phoneVerificationStore.remove(phone);
            throw new ServiceException("400-MEMBER_PHONE_CODE_EXPIRED", "인증번호가 만료되었습니다. 다시 요청해주세요.");
        }
        if (entry.getAttempts() >= memberPolicy.getPhoneMaxAttempts()) {
            phoneVerificationStore.remove(phone);
            throw new ServiceException("429-MEMBER_PHONE_TOO_MANY_ATTEMPTS", "인증 시도 횟수를 초과했습니다. 다시 요청해주세요.");
        }
        if (!entry.getCode().equals(code)) {
            entry.increaseAttempts();
            throw new ServiceException("400-MEMBER_PHONE_CODE_MISMATCH", "인증번호가 일치하지 않습니다.");
        }

        entry.markVerified(now.plusSeconds(memberPolicy.getPhoneVerifiedTtlSeconds()));
    }
}