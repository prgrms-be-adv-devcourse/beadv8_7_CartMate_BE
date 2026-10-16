package com.cartmate.cartmate.boundedContext.member.in.admin;

import com.cartmate.cartmate.boundedContext.member.app.MemberFacade;
import com.cartmate.cartmate.boundedContext.member.dto.*;
import com.cartmate.cartmate.global.page.PageResponse;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.global.security.SecurityUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/members")
@RequiredArgsConstructor
public class AdminMemberController {
    private final MemberFacade memberFacade;

    @GetMapping
    public RsData<PageResponse<AdminMemberResponse>> list(
            @ModelAttribute AdminMemberSearchRequest condition,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return new RsData<>("200-1", "회원 목록 조회 성공", memberFacade.searchMembersForAdmin(condition, page, size));
    }

    @GetMapping("/{id}")
    public RsData<AdminMemberDetailResponse> detail(@PathVariable Long id) {
        return new RsData<>("200-1", "회원 상세 조회 성공", memberFacade.getMemberDetailForAdmin(id));
    }

    @PostMapping("/{id}/suspend")
    public RsData<Void> suspend(@AuthenticationPrincipal SecurityUser admin,
                                @PathVariable Long id,
                                @Valid @RequestBody AdminSuspendRequest request) {
        memberFacade.suspendMember(admin.memberId(), id, request);
        return new RsData<>("200-1", "회원이 정지되었습니다.");
    }

    @PostMapping("/{id}/activate")
    public RsData<Void> activate(@PathVariable Long id) {
        memberFacade.activateMember(id);
        return new RsData<>("200-1", "정지가 해제되었습니다.");
    }

    @PostMapping("/bulk-status")
    public RsData<AdminBulkStatusResponse> bulkStatus(@AuthenticationPrincipal SecurityUser admin,
                                                      @Valid @RequestBody AdminBulkStatusRequest request) {
        AdminBulkStatusResponse result = memberFacade.changeMemberStatusBulk(admin.memberId(), request);
        return new RsData<>("200-1", "%d명 처리되었습니다.".formatted(result.successCount()), result);
    }
}