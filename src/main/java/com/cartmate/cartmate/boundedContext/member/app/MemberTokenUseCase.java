package com.cartmate.cartmate.boundedContext.member.app;

import com.cartmate.cartmate.boundedContext.member.domain.Member;
import com.cartmate.cartmate.boundedContext.member.domain.MemberRole;
import com.cartmate.cartmate.boundedContext.member.domain.RefreshToken;
import com.cartmate.cartmate.boundedContext.member.domain.Seller;
import com.cartmate.cartmate.boundedContext.member.dto.TokenPair;
import com.cartmate.cartmate.boundedContext.member.out.RefreshTokenRepository;
import com.cartmate.cartmate.boundedContext.member.out.SellerRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.security.JwtProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

// 액세스·리프레시 토큰 발급, 재발급(회전), 폐기
@Service
public class MemberTokenUseCase {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final MemberSupport memberSupport;
    private final JwtProvider jwtProvider;
    private final Duration rememberTtl;
    private final Duration sessionTtl;
    private final SellerRepository sellerRepository;

    public MemberTokenUseCase(RefreshTokenRepository refreshTokenRepository,
                              MemberSupport memberSupport,
                              JwtProvider jwtProvider,
                              SellerRepository sellerRepository,
                              @Value("${custom.jwt.refreshTokenRememberDays:30}") long rememberDays,
                              @Value("${custom.jwt.refreshTokenSessionHours:24}") long sessionHours) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.memberSupport = memberSupport;
        this.jwtProvider = jwtProvider;
        this.sellerRepository = sellerRepository;
        this.rememberTtl = Duration.ofDays(rememberDays);
        this.sessionTtl = Duration.ofHours(sessionHours);
    }

    public TokenPair issue(Member member, boolean remember) {
        // 판매자면 판매자 ID를 토큰에 넣는다. 재발급도 이 메서드를 거치므로 항상 DB 기준 최신 값이다.
        Long sellerId = member.getRole() == MemberRole.SELLER
                ? sellerRepository.findByMemberId(member.getId()).map(Seller::getId).orElse(null)
                : null;
        String accessToken = jwtProvider.createAccessToken(member.getId(), member.getEmail(), member.getRole().name(), sellerId);

        Duration ttl = remember ? rememberTtl : sessionTtl;
        String rawRefresh = newRandomToken();
        refreshTokenRepository.save(RefreshToken.issue(
                member.getId(), hash(rawRefresh), LocalDateTime.now().plus(ttl), remember));

        return new TokenPair(accessToken, rawRefresh, remember, ttl);
    }

    // 재발급: 쓴 리프레시 토큰은 지우고 새로 발급한다 (회전)
    public TokenPair refresh(String rawRefresh) {
        if (rawRefresh == null || rawRefresh.isBlank()) {
            throw invalid();
        }
        String hash = hash(rawRefresh);
        RefreshToken saved = refreshTokenRepository.findByTokenHash(hash).orElseThrow(this::invalid);

        // 동시에 같은 토큰으로 요청해도 먼저 지운 한 건만 통과
        if (refreshTokenRepository.deleteByTokenHash(hash) == 0) {
            throw invalid();
        }
        if (saved.isExpired(LocalDateTime.now())) {
            throw invalid();
        }

        Member member = memberSupport.findById(saved.getMemberId()).orElseThrow(this::invalid);
        member.validateCanLogin(); // 정지·탈퇴 회원이면 여기서 예외 → 재로그인 불가

        return issue(member, saved.isRemember());
    }

    // 로그아웃: 이 기기의 토큰만 폐기
    public void revoke(String rawRefresh) {
        if (rawRefresh != null && !rawRefresh.isBlank()) {
            refreshTokenRepository.deleteByTokenHash(hash(rawRefresh));
        }
    }

    // 정지·탈퇴: 모든 기기의 토큰 폐기
    public void revokeAll(Long memberId) {
        refreshTokenRepository.deleteAllByMemberId(memberId);
    }

    public int deleteExpired() {
        return refreshTokenRepository.deleteExpired(LocalDateTime.now());
    }

    private String newRandomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private ServiceException invalid() {
        return new ServiceException("401-AUTH_REFRESH_INVALID", "로그인이 만료되었습니다. 다시 로그인해주세요.");
    }
}