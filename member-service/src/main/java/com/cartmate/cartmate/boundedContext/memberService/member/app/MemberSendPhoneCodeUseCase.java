package com.cartmate.cartmate.boundedContext.memberService.member.app;

import com.cartmate.cartmate.boundedContext.memberService.member.domain.MemberPolicy;
import com.cartmate.cartmate.boundedContext.memberService.member.out.PhoneVerificationStore;
import com.cartmate.cartmate.boundedContext.memberService.member.out.VerificationCodeStore.IssueResult;
import com.cartmate.cartmate.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberSendPhoneCodeUseCase {

    private final PhoneVerificationStore phoneVerificationStore;
    private final VerificationCodeHasher hasher;
    private final MemberPolicy memberPolicy;

    public int send(String phone) {
        String code = hasher.newCode();
        int ttl = memberPolicy.getPhoneCodeTtlSeconds();

        IssueResult issued = phoneVerificationStore.issue(phone, code, Instant.now());
        switch (issued.status()) {
            case TOO_SOON -> throw new ServiceException("429-MEMBER_PHONE_RESEND_TOO_SOON",
                    (issued.retryAfter().toSeconds() + 1) + "초 후에 다시 요청해주세요.");
            case TOO_MANY_SENDS -> throw new ServiceException("429-MEMBER_PHONE_TOO_MANY_SENDS",
                    "인증번호를 너무 많이 요청했습니다. 잠시 후 다시 시도해주세요.");
            case TOO_MANY_ATTEMPTS -> throw new ServiceException("429-MEMBER_PHONE_TOO_MANY_ATTEMPTS",
                    "인증 시도 횟수를 초과했습니다. 잠시 후 다시 시도해주세요.");
            case ISSUED -> {
            }
        }

        // 실제 문자 발송 대신 로그로 출력 (문자 서비스 연동 시 이 부분만 교체, 실패하면 releaseCooldown)
        log.info("[휴대폰 인증] {} → 인증번호 {} ({}초 유효)", phone, code, ttl);

        return ttl;
    }
}