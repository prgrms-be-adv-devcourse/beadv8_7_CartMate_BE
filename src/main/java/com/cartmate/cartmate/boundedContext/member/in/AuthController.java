package com.cartmate.cartmate.boundedContext.member.in;

import com.cartmate.cartmate.boundedContext.member.app.MemberFacade;
import com.cartmate.cartmate.boundedContext.member.dto.PhoneSendRequest;
import com.cartmate.cartmate.boundedContext.member.dto.PhoneSendResponse;
import com.cartmate.cartmate.boundedContext.member.dto.PhoneVerifyRequest;
import com.cartmate.cartmate.boundedContext.member.dto.SignupRequest;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.shared.member.dto.MemberDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final MemberFacade memberFacade;

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
}