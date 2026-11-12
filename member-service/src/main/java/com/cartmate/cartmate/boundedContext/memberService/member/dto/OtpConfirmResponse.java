package com.cartmate.cartmate.boundedContext.memberService.member.dto;

import com.cartmate.cartmate.shared.member.dto.MemberDto;

import java.util.List;

// OTP 등록 완료: 백업 코드 10개는 이 응답에서 한 번만 보여 준다 (서버엔 해시만 남는다)
public record OtpConfirmResponse(MemberDto member, List<String> backupCodes) {
}
