package com.cartmate.cartmate.boundedContext.memberService.member.dto;

/**
 * 로그인 시도의 결과 (서버 안에서만 쓴다). result와 challenge 중 하나만 채워진다.
 *
 * @param result                로그인 완료 (토큰 발급)
 * @param trustedDeviceToken    쿠키에 새로 넣을 신뢰 기기 토큰 (등록·교체했을 때만)
 * @param challenge             2차 인증 필요 (토큰 없음)
 * @param otpEnrollmentRequired 관리자인데 OTP가 아직 없음 → 이 토큰으로는 관리자 API를 못 쓰고 OTP 등록만 할 수 있다
 * @param remainingBackupCodes  백업 코드로 로그인했을 때 남은 개수 (아니면 null)
 */
public record LoginOutcome(LoginResult result, String trustedDeviceToken, MfaChallengeResponse challenge,
                           boolean otpEnrollmentRequired, Integer remainingBackupCodes) {

    public static LoginOutcome loggedIn(LoginResult result, String trustedDeviceToken) {
        return new LoginOutcome(result, trustedDeviceToken, null, false, null);
    }

    public static LoginOutcome challenged(MfaChallengeResponse challenge) {
        return new LoginOutcome(null, null, challenge, false, null);
    }

    public LoginOutcome withOtpEnrollmentRequired() {
        return new LoginOutcome(result, trustedDeviceToken, challenge, true, remainingBackupCodes);
    }

    public LoginOutcome withRemainingBackupCodes(Integer remaining) {
        return new LoginOutcome(result, trustedDeviceToken, challenge, otpEnrollmentRequired, remaining);
    }

    public boolean mfaRequired() {
        return challenge != null;
    }
}
