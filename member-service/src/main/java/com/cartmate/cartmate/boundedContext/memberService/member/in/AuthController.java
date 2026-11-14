package com.cartmate.cartmate.boundedContext.memberService.member.in;

import com.cartmate.cartmate.boundedContext.memberService.member.app.MemberFacade;
import com.cartmate.cartmate.boundedContext.memberService.member.dto.*;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.global.security.AuthCookieManager;
import com.cartmate.cartmate.shared.member.dto.MemberDto;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final MemberFacade memberFacade;
    private final AuthCookieManager authCookieManager;
    private final TrustedDeviceCookie trustedDeviceCookie;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public RsData<MemberDto> signup(@Valid @RequestBody SignupRequest request) {
        MemberDto memberDto = memberFacade.join(request);
        return new RsData<>("201-1", "회원가입이 완료되었습니다.", memberDto);
    }

    public record AvailabilityResponse(boolean available) {
    }

    // 가입 이메일 확인: 코드 발송 (60초 뒤부터 재발송, 최대 5번). 응답 모양은 휴대폰 발송과 같다
    @PostMapping("/email/send")
    public RsData<PhoneSendResponse> sendEmailCode(@Valid @RequestBody EmailSendRequest request) {
        int ttl = memberFacade.sendEmailCode(request.email());
        return new RsData<>("200-1", "인증 메일이 발송되었습니다.", new PhoneSendResponse(ttl));
    }

    @PostMapping("/email/verify")
    public RsData<Void> verifyEmailCode(@Valid @RequestBody EmailVerifyRequest request) {
        memberFacade.verifyEmailCode(request.email(), request.code());
        return new RsData<>("200-1", "이메일 인증이 완료되었습니다.");
    }

    @PostMapping("/phone/send")
    public RsData<PhoneSendResponse> sendPhoneCode(@Valid @RequestBody PhoneSendRequest request) {
        int ttl = memberFacade.sendPhoneCode(request.phone());
        return new RsData<>("200-1", "인증번호가 발송되었습니다.", new PhoneSendResponse(ttl));
    }

    @PostMapping("/phone/verify")
    public RsData<Void> verifyPhoneCode(@Valid @RequestBody PhoneVerifyRequest request) {
        memberFacade.verifyPhoneCode(request.phone(), request.code());
        return new RsData<>("200-1", "휴대폰 인증이 완료되었습니다.");
    }

    @GetMapping("/check-email")
    public RsData<AvailabilityResponse> checkEmail(@RequestParam String email) {
        boolean available = memberFacade.isEmailAvailable(email.trim());
        return new RsData<>("200-1",
                available ? "사용 가능한 이메일입니다." : "이미 사용 중인 이메일입니다.",
                new AvailabilityResponse(available));
    }

    @GetMapping("/check-nickname")
    public RsData<AvailabilityResponse> checkNickname(@RequestParam String nickname) {
        boolean available = memberFacade.isNicknameAvailable(nickname.trim());
        return new RsData<>("200-1",
                available ? "사용 가능한 닉네임입니다." : "이미 사용 중인 닉네임입니다.",
                new AvailabilityResponse(available));
    }

    // 비밀번호가 맞으면: 2차 인증이 필요 없을 때(신뢰 기기 등)만 토큰, 아니면 mfaRequired=true + challengeId (토큰 없음)
    @PostMapping("/login")
    public RsData<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                       @CookieValue(name = TrustedDeviceCookie.NAME, required = false) String trustedDevice,
                                       HttpServletResponse response) {
        LoginOutcome outcome = memberFacade.login(request.email(), request.password(), request.remember(), trustedDevice);
        if (outcome.mfaRequired()) {
            return new RsData<>("200-2", "이메일로 보낸 인증번호를 입력해주세요.", LoginResponse.of(outcome));
        }
        completeLogin(response, outcome);
        return new RsData<>("200-1", "로그인되었습니다.", LoginResponse.of(outcome));
    }

    @PostMapping("/mfa/verify")
    public RsData<LoginResponse> verifyLoginMfa(@Valid @RequestBody MfaVerifyRequest request,
                                                @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
                                                HttpServletResponse response) {
        LoginOutcome outcome = memberFacade.verifyLoginMfa(request.challengeId(), request.code(), request.trustDevice(), userAgent);
        completeLogin(response, outcome);
        return new RsData<>("200-1", "로그인되었습니다.", LoginResponse.of(outcome));
    }

    @PostMapping("/mfa/resend")
    public RsData<PhoneSendResponse> resendLoginMfa(@Valid @RequestBody MfaResendRequest request) {
        int ttl = memberFacade.resendLoginMfa(request.challengeId());
        return new RsData<>("200-1", "인증 메일을 다시 보냈습니다.", new PhoneSendResponse(ttl));
    }

    private void completeLogin(HttpServletResponse response, LoginOutcome outcome) {
        addTokenCookies(response, outcome.result().tokens());
        if (outcome.trustedDeviceToken() != null) {
            response.addHeader(HttpHeaders.SET_COOKIE, trustedDeviceCookie.of(outcome.trustedDeviceToken()).toString());
        }
    }

    // 액세스 토큰이 만료되면 프론트가 자동으로 호출
    @PostMapping("/refresh")
    public RsData<Void> refresh(@CookieValue(name = AuthCookieManager.REFRESH_TOKEN, required = false) String refreshToken,
                                HttpServletResponse response) {
        try {
            addTokenCookies(response, memberFacade.refresh(refreshToken));
            return new RsData<>("200-1", "토큰이 재발급되었습니다.");
        } catch (ServiceException e) {
            clearTokenCookies(response); // 재발급 실패면 쿠키도 정리 → 프론트는 로그인 화면으로
            throw e;
        }
    }

    @PostMapping("/logout")
    public RsData<Void> logout(@CookieValue(name = AuthCookieManager.REFRESH_TOKEN, required = false) String refreshToken,
                               HttpServletResponse response) {
        memberFacade.logout(refreshToken);
        clearTokenCookies(response);
        return new RsData<>("200-1", "로그아웃되었습니다.");
    }

    private void addTokenCookies(HttpServletResponse response, TokenPair tokens) {
        response.addHeader(HttpHeaders.SET_COOKIE, authCookieManager.accessTokenCookie(tokens.accessToken()).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, authCookieManager
                .refreshTokenCookie(tokens.refreshToken(), tokens.remember(), tokens.refreshTokenMaxAge()).toString());
    }

    private void clearTokenCookies(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, authCookieManager.expiredAccessTokenCookie().toString());
        response.addHeader(HttpHeaders.SET_COOKIE, authCookieManager.expiredRefreshTokenCookie().toString());
    }
}