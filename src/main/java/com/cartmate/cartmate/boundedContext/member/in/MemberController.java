package com.cartmate.cartmate.boundedContext.member.in;

import com.cartmate.cartmate.boundedContext.member.app.MemberFacade;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.global.security.SecurityUser;
import com.cartmate.cartmate.shared.member.dto.MemberDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {
    private final MemberFacade memberFacade;

    @GetMapping("/me")
    public RsData<MemberDto> me(@AuthenticationPrincipal SecurityUser user) {
        MemberDto member = memberFacade.findById(user.memberId())
                .orElseThrow(() -> new ServiceException("404-MEMBER_NOT_FOUND", "회원을 찾을 수 없습니다."));
        return new RsData<>("200-1", "내 정보 조회 성공", member);
    }
}