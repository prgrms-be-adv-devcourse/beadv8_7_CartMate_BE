package com.cartmate.cartmate.boundedContext.memberService.member.dto;

// OTP 등록 시작: otpauthUri는 프론트가 QR로 그리고, secret은 앱에 직접 입력할 때 쓴다
public record OtpEnrollmentResponse(String otpauthUri, String secret) {
}
