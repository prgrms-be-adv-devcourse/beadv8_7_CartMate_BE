package com.cartmate.cartmate.boundedContext.memberService.member.dto;

import com.cartmate.cartmate.shared.member.dto.MemberDto;

// 로그인 응답: mfaRequired=false면 member(로그인 완료), true면 mfa(인증번호 입력 단계, 토큰 없음)
public record LoginResponse(boolean mfaRequired, MemberDto member, MfaChallengeResponse mfa) {

    public static LoginResponse of(LoginOutcome outcome) {
        return outcome.mfaRequired()
                ? new LoginResponse(true, null, outcome.challenge())
                : new LoginResponse(false, outcome.result().member(), null);
    }
}
