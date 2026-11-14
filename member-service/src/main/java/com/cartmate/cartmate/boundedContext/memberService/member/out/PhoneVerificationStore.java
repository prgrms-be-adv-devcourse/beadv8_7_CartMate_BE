package com.cartmate.cartmate.boundedContext.memberService.member.out;

import com.cartmate.cartmate.boundedContext.memberService.member.app.VerificationCodeHasher;
import com.cartmate.cartmate.boundedContext.memberService.member.domain.MemberPolicy;
import com.cartmate.cartmate.boundedContext.memberService.member.out.VerificationCodeStore.IssueResult;
import com.cartmate.cartmate.boundedContext.memberService.member.out.VerificationCodeStore.VerifyResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * 휴대폰 인증 상태 (가입, 휴대폰 번호 변경).
 *
 * <p>예전엔 서버 메모리(Map)에 있어서 회원 서비스를 두 대로 늘리면 A에서 보낸 번호를 B에서 확인하지 못했다.
 * 이메일 인증과 같은 Redis 저장소(VerificationCodeStore)에 두고, 코드는 원문 대신 HMAC 해시로 저장한다.
 */
@Component
@RequiredArgsConstructor
public class PhoneVerificationStore {

    private static final String PURPOSE = "phone:";
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final Duration SEND_WINDOW = Duration.ofMinutes(30);
    private static final int MAX_SENDS = 5;

    private final VerificationCodeStore codeStore;
    private final VerificationCodeHasher hasher;
    private final MemberPolicy policy;

    // 새 코드를 저장한다 (이전 코드는 바로 무효, 재발송은 60초 뒤부터, 30분에 최대 5번)
    public IssueResult issue(String phone, String code, Instant now) {
        String key = PURPOSE + phone;
        return codeStore.issue(key, hasher.hash(key, code), now, Duration.ofSeconds(policy.getPhoneCodeTtlSeconds()),
                RESEND_COOLDOWN, MAX_SENDS, policy.getPhoneMaxAttempts(), SEND_WINDOW);
    }

    public VerifyResult verify(String phone, String code, Instant now) {
        String key = PURPOSE + phone;
        return codeStore.verify(key, hasher.hash(key, code), now, policy.getPhoneMaxAttempts(),
                Duration.ofSeconds(policy.getPhoneVerifiedTtlSeconds()));
    }

    public boolean isVerified(String phone, Instant now) {
        return codeStore.isVerified(PURPOSE + phone, now);
    }

    // 문자를 못 보냈을 때 바로 다시 요청할 수 있게 (문자 서비스 연동 시 사용)
    public void releaseCooldown(String phone) {
        codeStore.releaseCooldown(PURPOSE + phone);
    }

    // 가입·번호 변경이 끝나면 지운다 (같은 인증으로 두 번 쓰지 못하게)
    public void remove(String phone) {
        codeStore.remove(PURPOSE + phone);
    }
}