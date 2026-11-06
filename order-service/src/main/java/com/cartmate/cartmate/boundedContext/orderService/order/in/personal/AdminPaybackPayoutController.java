package com.cartmate.cartmate.boundedContext.orderService.order.in.personal;

import com.cartmate.cartmate.boundedContext.orderService.order.app.personal.OrderFacade;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.PaybackPayoutStatus;
import com.cartmate.cartmate.boundedContext.orderService.order.dto.personal.PaybackPayoutResponse;
import com.cartmate.cartmate.global.rsData.RsData;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/orders/paybacks")
@RequiredArgsConstructor
public class AdminPaybackPayoutController {

    private final OrderFacade orderFacade;

    @GetMapping
    public RsData<List<PaybackPayoutResponse>> getPayouts(
            @RequestParam(defaultValue = "FAILED") PaybackPayoutStatus status
    ) {
        return new RsData<>("200-1", "페이백 지급 목록을 조회했습니다.", orderFacade.getPaybackPayouts(status));
    }

    @PostMapping("/{paybackId}/retry")
    public RsData<Void> retry(@PathVariable Long paybackId) {
        orderFacade.retryFailedPayback(paybackId);
        return new RsData<>("200-1", "페이백 지급을 다시 요청했습니다.");
    }
}
