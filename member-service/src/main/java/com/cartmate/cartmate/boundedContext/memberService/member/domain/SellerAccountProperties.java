package com.cartmate.cartmate.boundedContext.memberService.member.domain;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

// custom.member.seller-account 설정. 계좌 확인 통과 표시의 유효시간과 호출 횟수 제한이다.
@ConfigurationProperties(prefix = "custom.member.seller-account")
public record SellerAccountProperties(Duration verifiedTtl, int maxAttempts, Duration attemptWindow) {

    public SellerAccountProperties {
        verifiedTtl = verifiedTtl == null ? Duration.ofMinutes(10) : verifiedTtl;
        maxAttempts = maxAttempts <= 0 ? 5 : maxAttempts;
        attemptWindow = attemptWindow == null ? Duration.ofMinutes(10) : attemptWindow;
    }
}
