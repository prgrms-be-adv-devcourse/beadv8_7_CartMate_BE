package com.cartmate.cartmate.boundedContext.memberService.member.dto;

// 2차 인증이 필요할 때 로그인 응답. 토큰은 없다
public record MfaChallengeResponse(
        String challengeId,
        String method,           // EMAIL: 메일로 보낸 6자리 / OTP: OTP 앱의 6자리 또는 백업 코드 (관리자)
        String maskedEmail,      // bu***@cartmate.com (EMAIL일 때만)
        int expiresInSeconds     // 이 안에 입력
) {

    public static final String EMAIL = "EMAIL";
    public static final String OTP = "OTP";
}
