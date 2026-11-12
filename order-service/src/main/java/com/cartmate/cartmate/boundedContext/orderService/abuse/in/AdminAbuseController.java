package com.cartmate.cartmate.boundedContext.orderService.abuse.in;

import com.cartmate.cartmate.boundedContext.orderService.abuse.app.AbuseFacade;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseLevel;
import com.cartmate.cartmate.boundedContext.orderService.abuse.dto.AbuseMemberCaseResponse;
import com.cartmate.cartmate.boundedContext.orderService.abuse.dto.AbuseMemberSummaryResponse;
import com.cartmate.cartmate.boundedContext.orderService.abuse.dto.AdminAbuseReasonRequest;
import com.cartmate.cartmate.boundedContext.orderService.abuse.dto.AdminAbuseRestrictRequest;
import com.cartmate.cartmate.boundedContext.orderService.abuse.dto.AdminAbuseRetryResponse;
import com.cartmate.cartmate.boundedContext.orderService.abuse.exception.AbuseErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.global.security.SecurityUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/abuse/members")
@RequiredArgsConstructor
public class AdminAbuseController {

    private final AbuseFacade abuseFacade;
    private final AbuseJudgeRunner abuseJudgeRunner;

    @GetMapping
    public RsData<List<AbuseMemberSummaryResponse>> getFlaggedMembers(
            @RequestParam(defaultValue = "CAUTION") String minLevel,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return new RsData<>("200-1", "어뷰징 단계별 회원을 조회했습니다.",
                abuseFacade.findFlaggedMembers(parseLevel(minLevel), limit));
    }

    @GetMapping("/{memberId}")
    public RsData<AbuseMemberCaseResponse> getMemberCase(@PathVariable Long memberId) {
        return new RsData<>("200-1", "어뷰징 사례를 조회했습니다.", abuseFacade.getMemberCase(memberId));
    }

    @PostMapping("/{memberId}/restrictions")
    public RsData<Void> restrict(
            @AuthenticationPrincipal SecurityUser admin,
            @PathVariable Long memberId,
            @RequestBody AdminAbuseRestrictRequest request
    ) {
        abuseFacade.restrictByAdmin(memberId, admin.memberId(), request.hours(), request.reason());
        return new RsData<>("200-1", "회원을 수동으로 제한했습니다.");
    }

    @PostMapping("/{memberId}/release")
    public RsData<Void> release(
            @AuthenticationPrincipal SecurityUser admin,
            @PathVariable Long memberId,
            @RequestBody AdminAbuseReasonRequest request
    ) {
        abuseFacade.releaseByAdmin(memberId, admin.memberId(), request.reason());
        return new RsData<>("200-1", "제한을 해제하고 판정 기준점을 지금으로 옮겼습니다.");
    }

    @PostMapping("/{memberId}/failed-signals/retry")
    public RsData<AdminAbuseRetryResponse> retryFailedSignals(
            @AuthenticationPrincipal SecurityUser admin,
            @PathVariable Long memberId,
            @RequestBody AdminAbuseReasonRequest request
    ) {
        int reset = abuseFacade.resetFailedSignalsByAdmin(memberId, admin.memberId(), request.reason());
        boolean judged = abuseJudgeRunner.judge(memberId) == AbuseJudgeOutcome.JUDGED;
        String message = judged ? "실패 신호를 다시 판정했습니다." : "실패 신호를 되살렸고, 판정은 재판정 워커가 이어서 합니다.";
        return new RsData<>("200-1", message, new AdminAbuseRetryResponse(reset, judged));
    }

    private static AbuseLevel parseLevel(String value) {
        try {
            return AbuseLevel.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new ServiceException(AbuseErrorCode.ABUSE_INVALID_LEVEL);
        }
    }
}
