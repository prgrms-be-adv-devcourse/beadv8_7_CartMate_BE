package com.cartmate.cartmate.boundedContext.memberService.member.in;

import com.cartmate.cartmate.boundedContext.memberService.member.domain.MfaProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

// "이 기기 30일 기억" 쿠키. 로그인 요청(/api/auth)에만 실려 나간다. HttpOnly · Secure(운영) · SameSite=Lax
@Component
public class TrustedDeviceCookie {

    public static final String NAME = "trustedDevice";
    private static final String PATH = "/api/auth";

    private final MfaProperties properties;
    private final boolean secure;

    public TrustedDeviceCookie(MfaProperties properties, @Value("${custom.cookie.secure:false}") boolean secure) {
        this.properties = properties;
        this.secure = secure;
    }

    public ResponseCookie of(String token) {
        return ResponseCookie.from(NAME, token).httpOnly(true).secure(secure).sameSite("Lax").path(PATH)
                .maxAge(properties.trustedDeviceTtl()).build();
    }
}
