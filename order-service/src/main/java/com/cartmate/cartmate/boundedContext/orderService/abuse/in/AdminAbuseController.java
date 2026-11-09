package com.cartmate.cartmate.boundedContext.orderService.abuse.in;

import com.cartmate.cartmate.boundedContext.orderService.abuse.app.AbuseFacade;
import com.cartmate.cartmate.boundedContext.orderService.abuse.domain.AbuseLevel;
import com.cartmate.cartmate.boundedContext.orderService.abuse.dto.AbuseMemberCaseResponse;
import com.cartmate.cartmate.boundedContext.orderService.abuse.dto.AbuseMemberSummaryResponse;
import com.cartmate.cartmate.boundedContext.orderService.abuse.exception.AbuseErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.rsData.RsData;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/abuse/members")
@RequiredArgsConstructor
public class AdminAbuseController {

    private final AbuseFacade abuseFacade;

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

    private static AbuseLevel parseLevel(String value) {
        try {
            return AbuseLevel.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new ServiceException(AbuseErrorCode.ABUSE_INVALID_LEVEL);
        }
    }
}
