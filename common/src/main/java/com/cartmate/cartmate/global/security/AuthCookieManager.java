package com.cartmate.cartmate.global.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AuthCookieManager {
    public static final String ACCESS_TOKEN = "accessToken";
    public static final String REFRESH_TOKEN = "refreshToken";
    private static final String REFRESH_PATH = "/api/auth"; // 재발급·로그아웃 요청에만 실려 나간다

    private final boolean secure;
    private final JwtProvider jwtProvider;

    public AuthCookieManager(@Value("${custom.cookie.secure:false}") boolean secure, JwtProvider jwtProvider) {
        this.secure = secure;
        this.jwtProvider = jwtProvider;
    }

    public ResponseCookie accessTokenCookie(String token) {
        return base(ACCESS_TOKEN, token, "/")
                .maxAge(Duration.ofSeconds(jwtProvider.getAccessTokenExpireSeconds()))
                .build();
    }

    public ResponseCookie expiredAccessTokenCookie() {
        return base(ACCESS_TOKEN, "", "/").maxAge(0).build();
    }

    // 로그인 유지면 만료 시각까지 남는 쿠키, 아니면 브라우저를 닫으면 사라지는 쿠키
    public ResponseCookie refreshTokenCookie(String token, boolean remember, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = base(REFRESH_TOKEN, token, REFRESH_PATH);
        if (remember) {
            builder.maxAge(maxAge);
        }
        return builder.build();
    }

    public ResponseCookie expiredRefreshTokenCookie() {
        return base(REFRESH_TOKEN, "", REFRESH_PATH).maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String name, String value, String path) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(path);
    }
}