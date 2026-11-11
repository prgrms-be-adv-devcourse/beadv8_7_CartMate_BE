package com.cartmate.cartmate.boundedContext.memberService.member.dto;

/**
 * 로그인 시도의 결과 (서버 안에서만 쓴다). result와 challenge 중 하나만 채워진다.
 *
 * @param result             로그인 완료 (토큰 발급)
 * @param trustedDeviceToken 쿠키에 새로 넣을 신뢰 기기 토큰 (등록·교체했을 때만)
 * @param challenge          2차 인증 필요 (토큰 없음)
 */
public record LoginOutcome(LoginResult result, String trustedDeviceToken, MfaChallengeResponse challenge) {

    public static LoginOutcome loggedIn(LoginResult result, String trustedDeviceToken) {
        return new LoginOutcome(result, trustedDeviceToken, null);
    }

    public static LoginOutcome challenged(MfaChallengeResponse challenge) {
        return new LoginOutcome(null, null, challenge);
    }

    public boolean mfaRequired() {
        return challenge != null;
    }
}
