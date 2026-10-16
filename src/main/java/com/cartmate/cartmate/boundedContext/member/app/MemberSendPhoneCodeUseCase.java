package com.cartmate.cartmate.boundedContext.member.app;

import com.cartmate.cartmate.boundedContext.member.domain.MemberPolicy;
import com.cartmate.cartmate.boundedContext.member.out.PhoneVerificationStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberSendPhoneCodeUseCase {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PhoneVerificationStore phoneVerificationStore;
    private final MemberPolicy memberPolicy;

    public int send(String phone) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        int ttl = memberPolicy.getPhoneCodeTtlSeconds();

        phoneVerificationStore.saveCode(phone, code, LocalDateTime.now().plusSeconds(ttl));

        // 실제 문자 발송 대신 로그로 출력 (문자 서비스 연동 시 이 부분만 교체)
        log.info("[휴대폰 인증] {} → 인증번호 {} ({}초 유효)", phone, code, ttl);

        return ttl;
    }
}