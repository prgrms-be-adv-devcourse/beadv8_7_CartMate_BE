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
                            user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role())));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });
        chain.doFilter(request, response);
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