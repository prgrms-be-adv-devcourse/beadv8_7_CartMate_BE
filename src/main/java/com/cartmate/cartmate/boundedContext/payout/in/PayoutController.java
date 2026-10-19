package com.cartmate.cartmate.boundedContext.payout.in;

import com.cartmate.cartmate.boundedContext.payout.app.PayoutFacade;
import com.cartmate.cartmate.boundedContext.payout.app.dto.PendingPayoutResponse;
import com.cartmate.cartmate.boundedContext.payout.app.dto.SettlementDetailResponse;
import com.cartmate.cartmate.boundedContext.payout.app.dto.SettlementInfoResponse;
import com.cartmate.cartmate.boundedContext.payout.app.dto.SettlementResponse;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.global.security.SecurityUser;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/settlements")
@RequiredArgsConstructor
public class PayoutController {

    private final PayoutFacade payoutFacade;

    // 판매자 본인의 정산 내역. 지급 예정일(from~to, yyyy-MM-dd, 둘 다 포함)로 조회하고 같은 지급일·상태끼리 묶어서 내려간다.
    @GetMapping
    public RsData<List<SettlementResponse>> getMySettlements(
            @AuthenticationPrincipal SecurityUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return new RsData<>("200-1", "정산 내역 조회 성공했습니다.",
                payoutFacade.getMySettlements(user.memberId(), from, to));
    }

    // 판매자 본인의 정산 정책(수수료율, 지급일)과 정산 현황(다음 지급일, 지급 예정 금액, 지급 완료 금액).
    @GetMapping("/info")
    public RsData<SettlementInfoResponse> getMySettlementInfo(@AuthenticationPrincipal SecurityUser user) {
        return new RsData<>("200-1", "정산 정보 조회 성공했습니다.",
                payoutFacade.getMySettlementInfo(user.memberId()));
    }

    // 판매자 본인의 아직 지급되지 않은 구매확정 건. 지급 예정일이 이른 순으로 건별로 내려간다.
    @GetMapping("/pending")
    public RsData<List<PendingPayoutResponse>> getMyPendingPayouts(@AuthenticationPrincipal SecurityUser user) {
        return new RsData<>("200-1", "정산 대기 내역 조회 성공했습니다.",
                payoutFacade.getMyPendingPayouts(user.memberId()));
    }

    // 판매자 본인의 정산 상세. 정산 ID(지급 예정일+상태, 예: 20261115-COMPLETED)에 묶인 판매 건과 합계를 내려준다.
    @GetMapping("/{settlementId}")
    public RsData<SettlementDetailResponse> getMySettlementDetail(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable String settlementId
    ) {
        return new RsData<>("200-1", "정산 상세 조회 성공했습니다.",
                payoutFacade.getMySettlementDetail(user.memberId(), settlementId));
    }
}
