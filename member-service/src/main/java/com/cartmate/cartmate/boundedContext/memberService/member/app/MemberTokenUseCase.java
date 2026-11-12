package com.cartmate.cartmate.boundedContext.memberService.member.app;

import com.cartmate.cartmate.boundedContext.memberService.member.exception.MemberErrorCode;
import com.cartmate.cartmate.boundedContext.memberService.member.domain.Member;
import com.cartmate.cartmate.boundedContext.memberService.member.domain.MemberRole;
import com.cartmate.cartmate.boundedContext.memberService.member.domain.RefreshToken;
import com.cartmate.cartmate.boundedContext.memberService.member.domain.Seller;
import com.cartmate.cartmate.boundedContext.memberService.member.dto.TokenPair;
import com.cartmate.cartmate.boundedContext.memberService.member.out.RefreshTokenRepository;
import com.cartmate.cartmate.boundedContext.memberService.member.out.SellerRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.security.JwtProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 액세스·리프레시 토큰 발급, 재발급(회전), 폐기.
 *
 * <p>관리자 세션은 따로 다룬다: 로그인 유지(remember)를 무시하고, 세션 끝 시각을 로그인(OTP 확인) 때 정한 뒤
 * 재발급해도 늘리지 않는다 → 업무 세션 길이(기본 8시간)가 지나면 다시 OTP를 받아야 한다.
 * Access Token도 더 짧고(기본 15분), 세션 끝 시각을 넘지 않는다.
 */
@Service
public class MemberTokenUseCase {

    static final String AMR_PWD = "pwd";

    private final RefreshTokenRepository refreshTokenRepository;
    private final MemberSupport memberSupport;
    private final JwtProvider jwtProvider;
    private final Duration rememberTtl;
    private final Duration sessionTtl;
    private final Duration adminSessionTtl;
    private final long adminAccessTokenSeconds;
    private final SellerRepository sellerRepository;

    public MemberTokenUseCase(RefreshTokenRepository refreshTokenRepository,
                              MemberSupport memberSupport,
                              JwtProvider jwtProvider,
                              SellerRepository sellerRepository,
                              @Value("${custom.jwt.refreshTokenRememberDays:30}") long rememberDays,
                              @Value("${custom.jwt.refreshTokenSessionHours:24}") long sessionHours,
                              @Value("${custom.jwt.adminSessionHours:8}") long adminSessionHours,
                              @Value("${custom.jwt.adminAccessTokenExpireSeconds:900}") long adminAccessTokenSeconds) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.memberSupport = memberSupport;
        this.jwtProvider = jwtProvider;
        this.sellerRepository = sellerRepository;
        this.rememberTtl = Duration.ofDays(rememberDays);
        this.sessionTtl = Duration.ofHours(sessionHours);
        this.adminSessionTtl = Duration.ofHours(adminSessionHours);
        this.adminAccessTokenSeconds = adminAccessTokenSeconds;
    }

    // 비밀번호만 거친 로그인 (2차 인증 꺼짐, 신뢰 기기)
    public TokenPair issue(Member member, boolean remember) {
        return issue(member, remember, List.of(AMR_PWD));
    }

    // amr: 이 로그인에서 거친 인증 수단. 관리자 API는 otp가 있어야 통과한다
    public TokenPair issue(Member member, boolean remember, List<String> amr) {
        return issue(member, remember, amr, null);
    }

    // sessionEndsAt: 이어 받는 관리자 세션의 끝 시각 (새 로그인이면 null → 지금부터 새 수명)
    private TokenPair issue(Member member, boolean remember, List<String> amr, LocalDateTime sessionEndsAt) {
        LocalDateTime now = LocalDateTime.now();
        boolean admin = member.getRole() == MemberRole.ADMIN;
        boolean keep = remember && !admin; // 관리자는 로그인 유지 없음 (브라우저를 닫으면 끝)
        LocalDateTime expiresAt = sessionEndsAt != null
                ? sessionEndsAt
                : now.plus(admin ? adminSessionTtl : keep ? rememberTtl : sessionTtl);
        Duration remaining = Duration.between(now, expiresAt);

        // 판매자면 판매자 ID를 토큰에 넣는다. 재발급도 이 메서드를 거치므로 항상 DB 기준 최신 값이다.
        Long sellerId = member.getRole() == MemberRole.SELLER
                ? sellerRepository.findByMemberId(member.getId()).map(Seller::getId).orElse(null)
                : null;
        long accessSeconds = admin
                ? Math.min(adminAccessTokenSeconds, Math.max(1, remaining.toSeconds()))
                : jwtProvider.getAccessTokenExpireSeconds();
        String accessToken = jwtProvider.createAccessToken(member.getId(), member.getEmail(), member.getRole().name(),
                sellerId, amr, accessSeconds);

        String rawRefresh = SecureTokens.newToken();
        refreshTokenRepository.save(RefreshToken.issue(
                member.getId(), hash(rawRefresh), expiresAt, keep, String.join(",", amr)));

        return new TokenPair(accessToken, rawRefresh, keep, remaining);
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

        // 처음 로그인 때 거친 인증 수단을 그대로 이어 받는다 (재발급으로 OTP 세션이 되지 않게)
        List<String> amr = saved.getAmr() == null || saved.getAmr().isBlank()
                ? List.of(AMR_PWD) : List.of(saved.getAmr().split(","));
        // 관리자는 세션 끝 시각을 그대로 (재발급으로 늘어나지 않음). 일반 회원은 지금부터 새 수명
        LocalDateTime sessionEndsAt = member.getRole() == MemberRole.ADMIN ? saved.getExpiresAt() : null;
        return issue(member, saved.isRemember(), amr, sessionEndsAt);
    }

    // 로그아웃: 이 기기의 토큰만 폐기
    public void revoke(String rawRefresh) {
        if (rawRefresh != null && !rawRefresh.isBlank()) {
            refreshTokenRepository.deleteByTokenHash(hash(rawRefresh));
        }
    }

    // 정지·탈퇴·비밀번호 변경·OTP 등록: 모든 기기의 토큰 폐기
    public void revokeAll(Long memberId) {
        refreshTokenRepository.deleteAllByMemberId(memberId);
    }

    public int deleteExpired() {
        return refreshTokenRepository.deleteExpired(LocalDateTime.now());
    }

    private static String hash(String raw) {
        return SecureTokens.sha256(raw);
    }

    private ServiceException invalid() {
        return new ServiceException(MemberErrorCode.AUTH_REFRESH_INVALID);
    }
}
