package com.cartmate.cartmate.boundedContext.member.in;

import com.cartmate.cartmate.boundedContext.member.app.MemberFacade;
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
}