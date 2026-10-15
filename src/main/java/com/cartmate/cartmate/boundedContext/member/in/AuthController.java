package com.cartmate.cartmate.boundedContext.member.in;

import com.cartmate.cartmate.boundedContext.member.app.MemberFacade;
import com.cartmate.cartmate.boundedContext.member.dto.*;
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

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public RsData<MemberDto> signup(@Valid @RequestBody SignupRequest request) {
        MemberDto memberDto = memberFacade.join(request);
        return new RsData<>("201-1", "회원가입이 완료되었습니다.", memberDto);
    }

    public record AvailabilityResponse(boolean available) {
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

    @PostMapping("/login")
    public RsData<MemberDto> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        LoginResult result = memberFacade.login(request.email(), request.password());
        response.addHeader(HttpHeaders.SET_COOKIE, authCookieManager.accessTokenCookie(result.accessToken()).toString());
        return new RsData<>("200-1", "로그인되었습니다.", result.member());
    }

    @PostMapping("/logout")
    public RsData<Void> logout(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, authCookieManager.expiredAccessTokenCookie().toString());
        return new RsData<>("200-1", "로그아웃되었습니다.");
    }
}