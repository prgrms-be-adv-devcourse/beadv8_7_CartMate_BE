package com.cartmate.cartmate.boundedContext.memberService.member.domain;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

// custom.member.email 설정. 가입 이메일 인증번호의 유효시간과 발송·시도 횟수 제한이다.
@ConfigurationProperties(prefix = "custom.member.email")
public record EmailVerificationProperties(Duration codeTtl, Duration verifiedTtl, Duration resendCooldown,
                                          Duration sendWindow, int maxSends, int maxAttempts) {

    public EmailVerificationProperties {
        codeTtl = codeTtl == null ? Duration.ofMinutes(5) : codeTtl;
        verifiedTtl = verifiedTtl == null ? Duration.ofMinutes(30) : verifiedTtl;
        resendCooldown = resendCooldown == null ? Duration.ofSeconds(60) : resendCooldown;
        sendWindow = sendWindow == null ? Duration.ofMinutes(30) : sendWindow;
        maxSends = maxSends <= 0 ? 5 : maxSends;
        maxAttempts = maxAttempts <= 0 ? 5 : maxAttempts;
    }
}
