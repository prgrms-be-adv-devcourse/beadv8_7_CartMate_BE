package com.cartmate.cartmate.global.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 관리자 API 권한: 관리자는 OTP를 거친 토큰일 때만 ROLE_ADMIN (common에는 테스트 설정이 없어 회원 서비스에 둔다)
class JwtAuthenticationFilterAuthorityTest {

    private final JwtProvider jwtProvider = new JwtProvider("test-jwt-secret-test-jwt-secret-test-jwt-secret", 1800);

    @Test
    void OTP를_거치지_않은_관리자_토큰은_관리자_권한이_없다() {
        assertThat(JwtAuthenticationFilter.authorityOf(new SecurityUser(1L, "a@a.com", "ADMIN", null, List.of("pwd", "email"))))
                .isEqualTo("ROLE_ADMIN_PENDING");
        assertThat(JwtAuthenticationFilter.authorityOf(new SecurityUser(1L, "a@a.com", "ADMIN")))
                .isEqualTo("ROLE_ADMIN_PENDING");
    }

    @Test
    void OTP를_거친_관리자만_관리자_권한_다른_역할은_그대로() {
        assertThat(JwtAuthenticationFilter.authorityOf(new SecurityUser(1L, "a@a.com", "ADMIN", null, List.of("pwd", "otp"))))
                .isEqualTo("ROLE_ADMIN");
        assertThat(JwtAuthenticationFilter.authorityOf(new SecurityUser(2L, "b@b.com", "BUYER")))
                .isEqualTo("ROLE_BUYER");
    }

    @Test
    void 토큰의_amr이_그대로_읽히고_기존_4인자_토큰은_amr이_비어_있다() {
        String otpToken = jwtProvider.createAccessToken(1L, "a@a.com", "ADMIN", null, List.of("pwd", "otp"), 900);
        assertThat(jwtProvider.parse(otpToken)).hasValueSatisfying(user -> {
            assertThat(user.amr()).containsExactly("pwd", "otp");
            assertThat(user.passedOtp()).isTrue();
        });

        String legacy = jwtProvider.createAccessToken(1L, "a@a.com", "ADMIN", null);
        assertThat(jwtProvider.parse(legacy)).hasValueSatisfying(user -> assertThat(user.amr()).isEmpty());
    }
}
