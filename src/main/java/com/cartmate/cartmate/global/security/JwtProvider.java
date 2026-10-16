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

    public String createAccessToken(Long memberId, String email, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(memberId))
                .claim("email", email)
                .claim("role", role)
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

            return Optional.of(new SecurityUser(
                    Long.valueOf(claims.getSubject()),
                    claims.get("email", String.class),
                    claims.get("role", String.class)
            ));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty(); // 위조, 만료, 형식 오류 모두 "인증 안 됨"으로 처리
        }
    }

    public long getAccessTokenExpireSeconds() {
        return accessTokenExpireSeconds;
    }
}