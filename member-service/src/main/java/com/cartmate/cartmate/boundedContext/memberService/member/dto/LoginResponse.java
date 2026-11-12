package com.cartmate.cartmate.boundedContext.memberService.member.dto;

import com.cartmate.cartmate.shared.member.dto.MemberDto;

// mfaRequired=false면 member(로그인 완료), true면 mfa(코드 입력 단계, 토큰 없음).
// otpEnrollmentRequired=true면 관리자 OTP 등록 화면으로 보낸다 (그 전엔 관리자 API 403)
public record LoginResponse(boolean mfaRequired, MemberDto member, MfaChallengeResponse mfa,
                            boolean otpEnrollmentRequired, Integer remainingBackupCodes) {

    public static LoginResponse of(LoginOutcome outcome) {
        return outcome.mfaRequired()
                ? new LoginResponse(true, null, outcome.challenge(), false, null)
                : new LoginResponse(false, outcome.result().member(), null,
                        outcome.otpEnrollmentRequired(), outcome.remainingBackupCodes());
    }
}
