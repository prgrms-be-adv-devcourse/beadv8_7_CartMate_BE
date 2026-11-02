package com.cartmate.cartmate.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

@Component
public class JwtProvider {
    private final SecretKey key;
    private final long accessTokenExpireSeconds;

    public JwtProvider(
            @Value("${custom.jwt.secret}") String secret,
            @Value("${custom.jwt.accessTokenExpireSeconds:1800}") long accessTokenExpireSeconds
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpireSeconds = accessTokenExpireSeconds;
    }

    public String createAccessToken(Long memberId, String email, String role, Long sellerId) {
        Date now = new Date();
        var builder = Jwts.builder()
                .subject(String.valueOf(memberId))
                .claim("email", email)
                .claim("role", role);
        if (sellerId != null) {
            builder.claim("sellerId", sellerId);   // 판매자만 넣는다
        }
        return builder
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenExpireSeconds * 1000))
                .signWith(key)
                .compact();
    }

    public Optional<SecurityUser> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // 판매자 토큰에만 sellerId가 있다. 숫자는 Integer로 읽힐 수도 있어 Number로 받아 Long으로 바꾼다.
            Number sellerId = claims.get("sellerId", Number.class);
            return Optional.of(new SecurityUser(
                    Long.valueOf(claims.getSubject()),
                    claims.get("email", String.class),
                    claims.get("role", String.class),
                    sellerId == null ? null : sellerId.longValue()
            ));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty(); // 위조, 만료, 형식 오류 모두 "인증 안 됨"으로 처리
        }
    }

    public long getAccessTokenExpireSeconds() {
        return accessTokenExpireSeconds;
    }
}