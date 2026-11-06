package com.cartmate.cartmate.boundedContext.paymentService.payout.in.internal;

import com.cartmate.cartmate.boundedContext.paymentService.payout.app.PayoutFacade;
import com.cartmate.cartmate.global.rsData.RsData;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 정산 내부 API (결제 서버 소속). 다른 서버만 부른다 — 내부 API 키 필터가 확인한다.
@Hidden
@RestController
@RequestMapping("/internal/payouts")
@RequiredArgsConstructor
public class PayoutInternalController {

    private final PayoutFacade payoutFacade;

    // 회원 탈퇴 확인: 판매자의 지급 전 정산 수
    @GetMapping("/pending-count")
    public RsData<Long> pendingCount(@RequestParam Long sellerId) {
        return new RsData<>("200-1", "지급 전 정산 수", payoutFacade.countPendingOfSeller(sellerId));
    }
}