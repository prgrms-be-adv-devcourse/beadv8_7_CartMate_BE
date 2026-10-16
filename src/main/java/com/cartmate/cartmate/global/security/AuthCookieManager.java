package com.cartmate.cartmate.global.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AuthCookieManager {
    public static final String ACCESS_TOKEN = "accessToken";

    private final boolean secure;
    private final JwtProvider jwtProvider;

    public AuthCookieManager(@Value("${custom.cookie.secure:false}") boolean secure, JwtProvider jwtProvider) {
        this.secure = secure;
        this.jwtProvider = jwtProvider;
    }

    public ResponseCookie accessTokenCookie(String token) {
        return base(token)
                .maxAge(Duration.ofSeconds(jwtProvider.getAccessTokenExpireSeconds()))
                .build();
    }

    public ResponseCookie expiredAccessTokenCookie() {
        return base("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(ACCESS_TOKEN, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/");
    }
}