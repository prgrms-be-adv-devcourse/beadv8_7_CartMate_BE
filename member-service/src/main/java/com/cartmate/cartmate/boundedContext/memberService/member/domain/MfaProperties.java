package com.cartmate.cartmate.boundedContext.memberService.member.domain;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 로그인 2차 인증 설정 (custom.member.mfa). 인증번호 자체의 규칙(유효 시간·재발송 간격·횟수)은 가입 이메일 인증과 같은 값을 쓴다.
 *
 * @param required          일반 회원에게 2차 인증을 요구할지 (운영 true, 로컬 개발에서만 끌 수 있다. 관리자는 항상)
 * @param challengeTtl      비밀번호를 맞힌 뒤 코드를 넣을 수 있는 시간 (재발송 포함)
 * @param trustedDeviceTtl  "이 기기 30일 기억"의 유효 기간
 * @param accountFailLimit  계정 하나에 accountFailWindow 동안 틀릴 수 있는 횟수 (새로 로그인해 우회하는 것을 막는다)
 */
@ConfigurationProperties(prefix = "custom.member.mfa")
public record MfaProperties(Boolean required, Duration challengeTtl, Duration trustedDeviceTtl,
                            int accountFailLimit, Duration accountFailWindow) {

    public MfaProperties {
        required = required == null ? Boolean.TRUE : required;
        challengeTtl = challengeTtl == null ? Duration.ofMinutes(10) : challengeTtl;
        trustedDeviceTtl = trustedDeviceTtl == null ? Duration.ofDays(30) : trustedDeviceTtl;
        accountFailLimit = accountFailLimit <= 0 ? 10 : accountFailLimit;
        accountFailWindow = accountFailWindow == null ? Duration.ofHours(1) : accountFailWindow;
    }
}
