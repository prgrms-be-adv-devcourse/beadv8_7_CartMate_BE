package com.cartmate.cartmate.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/**
 * /internal/** 은 서버끼리만 부르는 API다. 외부 차단은 Gateway가 하고, 앱에서도 내부 API 키를 한 번 더 확인한다.
 * 키가 없거나 다르면 403. 로그인 토큰은 쓰지 않는다 (서버가 서버를 부르는 요청이라 사용자가 없을 수 있음).
 */
public class InternalApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Api-Key";

    private final String apiKey;

    // 키가 비어 있으면 빈 헤더로 누구나 통과할 수 있다 → 앱이 아예 뜨지 않게 막는다
    public InternalApiKeyFilter(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("cartmate.internal.api-key(INTERNAL_API_KEY)가 설정되지 않았습니다.");
        }
        this.apiKey = apiKey;
    }



    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String given = request.getHeader(HEADER);
        if (given == null || !MessageDigest.isEqual(
                given.getBytes(StandardCharsets.UTF_8), apiKey.getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"resultCode\":\"403-INTERNAL_ONLY\",\"msg\":\"내부 API입니다.\",\"data\":null}");
            return;
        }
        chain.doFilter(request, response);
    }
}