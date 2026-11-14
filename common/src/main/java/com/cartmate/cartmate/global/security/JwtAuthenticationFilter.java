package com.cartmate.cartmate.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtProvider jwtProvider;
    private final BlockedMemberRegistry blockedMemberRegistry;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        resolveToken(request)
                .flatMap(jwtProvider::parse)
                .filter(user -> !blockedMemberRegistry.isBlocked(user.memberId())) // 정지 회원은 즉시 차단
                .ifPresent(user -> {
                    var authentication = new UsernamePasswordAuthenticationToken(
                            user, null, List.of(new SimpleGrantedAuthority(authorityOf(user))));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });
        chain.doFilter(request, response);
    }

    // 관리자는 OTP(또는 백업 코드)를 거친 토큰일 때만 ROLE_ADMIN. 아니면 ROLE_ADMIN_PENDING → /api/admin/** 는 403
    // (OTP 등록 전·이메일 인증만 거친 관리자 토큰으로는 관리자 API를 쓸 수 없다. 모든 서비스가 이 필터를 쓴다)
    static String authorityOf(SecurityUser user) {
        if ("ADMIN".equals(user.role()) && !user.passedOtp()) {
            return "ROLE_ADMIN_PENDING";
        }
        return "ROLE_" + user.role();
    }

    private Optional<String> resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return Optional.of(header.substring(7));
        }
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (AuthCookieManager.ACCESS_TOKEN.equals(cookie.getName())) {
                    return Optional.of(cookie.getValue());
                }
            }
        }
        return Optional.empty();
    }
}