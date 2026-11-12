package com.cartmate.cartmate.global.security;

import java.util.List;

/**
 * 토큰에서 꺼낸 로그인 사용자.
 *
 * @param amr 이 로그인에서 거친 인증 수단 (pwd: 비밀번호, email: 이메일 코드, otp: OTP 앱·백업 코드).
 *            관리자는 amr에 otp가 있어야 관리자 API를 쓸 수 있다 (JwtAuthenticationFilter)
 */
public record SecurityUser(
        Long memberId,
        String email,
        String role,
        Long sellerId,   // 판매자면 판매자 ID, 구매자·관리자면 null
        List<String> amr
) {

    public static final String AMR_OTP = "otp";

    public SecurityUser {
        amr = amr == null ? List.of() : List.copyOf(amr);
    }

    public SecurityUser(Long memberId, String email, String role, Long sellerId) {
        this(memberId, email, role, sellerId, List.of());
    }

    // 판매자 정보가 필요 없는 곳(기존 테스트 등)은 그대로 쓸 수 있게
    public SecurityUser(Long memberId, String email, String role) {
        this(memberId, email, role, null, List.of());
    }

    public boolean passedOtp() {
        return amr.contains(AMR_OTP);
    }
}
