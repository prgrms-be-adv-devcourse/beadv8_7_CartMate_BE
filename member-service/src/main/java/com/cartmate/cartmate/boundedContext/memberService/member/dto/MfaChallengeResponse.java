package com.cartmate.cartmate.boundedContext.memberService.member.dto;

// 2차 인증이 필요할 때 로그인 응답에 담는 값
public record MfaChallengeResponse(
        String challengeId,
        String maskedEmail,      // bu***@cartmate.com
        int expiresInSeconds     // 인증번호 유효 시간
) {
}
