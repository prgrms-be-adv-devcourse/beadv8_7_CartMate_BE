package com.cartmate.cartmate.boundedContext.member.in;

import com.cartmate.cartmate.boundedContext.member.app.MemberFacade;
import com.cartmate.cartmate.boundedContext.member.dto.ChangePasswordRequest;
import com.cartmate.cartmate.boundedContext.member.dto.MemberProfileResponse;
import com.cartmate.cartmate.boundedContext.member.dto.MyPageDashboardResponse;
import com.cartmate.cartmate.boundedContext.member.dto.UpdateProfileRequest;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.global.security.SecurityUser;
import com.cartmate.cartmate.shared.member.dto.MemberDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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

    //임시 조회용
    @GetMapping("/me/dashboard")
    public RsData<MyPageDashboardResponse> myPageDashboard(@AuthenticationPrincipal SecurityUser user) {
        return new RsData<>("200-1", "마이페이지 조회 성공", memberFacade.getMyPageDashboard(user.memberId()));
    }

    @GetMapping("/me/profile")
    public RsData<MemberProfileResponse> profile(@AuthenticationPrincipal SecurityUser user) {
        return new RsData<>("200-1", "회원정보 조회 성공", memberFacade.getProfile(user.memberId()));
    }

    @PatchMapping("/me")
    public RsData<MemberProfileResponse> updateProfile(@AuthenticationPrincipal SecurityUser user,
                                                       @Valid @RequestBody UpdateProfileRequest request) {
        return new RsData<>("200-1", "회원정보가 수정되었습니다.", memberFacade.updateProfile(user.memberId(), request));
    }

    @PatchMapping("/me/password")
    public RsData<Void> changePassword(@AuthenticationPrincipal SecurityUser user,
                                       @Valid @RequestBody ChangePasswordRequest request) {
        memberFacade.changePassword(user.memberId(), request);
        return new RsData<>("200-1", "비밀번호가 변경되었습니다.");
    }
}