package com.cartmate.cartmate.boundedContext.memberService.member.app;

import com.cartmate.cartmate.boundedContext.memberService.member.domain.Member;
import com.cartmate.cartmate.boundedContext.memberService.member.domain.MemberRole;
import com.cartmate.cartmate.boundedContext.memberService.member.domain.RefreshToken;
import com.cartmate.cartmate.boundedContext.memberService.member.dto.TokenPair;
import com.cartmate.cartmate.boundedContext.memberService.member.out.RefreshTokenRepository;
import com.cartmate.cartmate.boundedContext.memberService.member.out.SellerRepository;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.security.JwtProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 관리자 세션: 로그인 유지 없음, OTP 확인 시각부터 8시간 고정 (재발급해도 늘지 않음), amr 이어 받기
class MemberTokenUseCaseTest {

    private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
    private final MemberSupport memberSupport = mock(MemberSupport.class);
    private final JwtProvider jwtProvider = new JwtProvider("test-jwt-secret-test-jwt-secret-test-jwt-secret", 1800);
    private final MemberTokenUseCase useCase = new MemberTokenUseCase(refreshTokens, memberSupport, jwtProvider,
            mock(SellerRepository.class), 30, 24, 8, 900);

    private Member member(long id, MemberRole role) {
        Member member = Member.create("m@cartmate.com", "encoded", "회원", "010-1234-5678", LocalDate.of(1990, 1, 1));
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "role", role);
        when(memberSupport.findById(id)).thenReturn(Optional.of(member));
        return member;
    }

    private RefreshToken lastSaved(int times) {
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens, times(times)).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void 관리자는_로그인_유지를_해도_8시간_세션_Access_Token은_15분() {
        Member admin = member(9L, MemberRole.ADMIN);

        TokenPair tokens = useCase.issue(admin, true, List.of("pwd", "otp"));

        RefreshToken saved = lastSaved(1);
        assertThat(tokens.remember()).isFalse();
        assertThat(saved.isRemember()).isFalse();
        assertThat(saved.getExpiresAt()).isCloseTo(LocalDateTime.now().plusHours(8), within(5, java.time.temporal.ChronoUnit.SECONDS));
        assertThat(saved.getAmr()).isEqualTo("pwd,otp");
        assertThat(jwtProvider.parse(tokens.accessToken())).hasValueSatisfying(user -> assertThat(user.passedOtp()).isTrue());
    }

    @Test
    void 관리자_재발급은_세션_끝_시각을_늘리지_않고_amr을_이어_받는다() {
        member(9L, MemberRole.ADMIN);
        LocalDateTime sessionEnd = LocalDateTime.now().plusHours(1); // 7시간 전 OTP 확인 → 1시간 남음
        RefreshToken current = RefreshToken.issue(9L, "h", sessionEnd, false, "pwd,otp");
        when(refreshTokens.findByTokenHash(anyString())).thenReturn(Optional.of(current));
        when(refreshTokens.deleteByTokenHash(anyString())).thenReturn(1);

        TokenPair tokens = useCase.refresh("raw");

        RefreshToken next = lastSaved(1);
        assertThat(next.getExpiresAt()).isEqualTo(sessionEnd);
        assertThat(next.getAmr()).isEqualTo("pwd,otp");
        assertThat(tokens.refreshTokenMaxAge()).isLessThanOrEqualTo(Duration.ofHours(1));
    }

    @Test
    void 관리자_세션이_끝나면_재발급이_실패해_다시_OTP를_받는다() {
        member(9L, MemberRole.ADMIN);
        RefreshToken ended = RefreshToken.issue(9L, "h", LocalDateTime.now().minusSeconds(1), false, "pwd,otp");
        when(refreshTokens.findByTokenHash(anyString())).thenReturn(Optional.of(ended));
        when(refreshTokens.deleteByTokenHash(anyString())).thenReturn(1);

        assertThatThrownBy(() -> useCase.refresh("raw")).isInstanceOf(ServiceException.class)
                .hasMessageContaining("다시 로그인");
    }

    @Test
    void 일반_회원_재발급은_지금부터_새_수명이고_amr이_없던_예전_토큰은_비밀번호만으로_본다() {
        member(1L, MemberRole.BUYER);
        RefreshToken old = RefreshToken.issue(1L, "h", LocalDateTime.now().plusMinutes(1), true, null);
        when(refreshTokens.findByTokenHash(anyString())).thenReturn(Optional.of(old));
        when(refreshTokens.deleteByTokenHash(anyString())).thenReturn(1);

        useCase.refresh("raw");

        RefreshToken next = lastSaved(1);
        assertThat(next.getExpiresAt()).isAfter(LocalDateTime.now().plusDays(29)); // 로그인 유지 30일
        assertThat(next.getAmr()).isEqualTo("pwd");
        verify(refreshTokens).save(any());
    }
}
