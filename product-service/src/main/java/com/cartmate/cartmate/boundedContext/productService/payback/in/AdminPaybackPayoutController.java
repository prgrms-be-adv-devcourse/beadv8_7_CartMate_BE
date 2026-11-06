package com.cartmate.cartmate.boundedContext.productService.payback.in;

import com.cartmate.cartmate.boundedContext.productService.payback.app.PaybackFacade;
import com.cartmate.cartmate.boundedContext.productService.payback.domain.PaybackPayoutStatus;
import com.cartmate.cartmate.boundedContext.productService.payback.dto.PaybackPayoutResponse;
import com.cartmate.cartmate.global.rsData.RsData;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/paybacks")
@RequiredArgsConstructor
public class AdminPaybackPayoutController {

    private final PaybackFacade paybackFacade;

    @GetMapping
    @Operation(operationId = "getAdminPaybackPayouts", summary = "어드민 페이백 지급 목록")
    public RsData<List<PaybackPayoutResponse>> getPayouts(
            @RequestParam(defaultValue = "FAILED") PaybackPayoutStatus status
    ) {
        return new RsData<>("200-1", "페이백 지급 목록을 조회했습니다.", paybackFacade.getPaybackPayouts(status));
    }

    @PostMapping("/{paybackId}/retry")
    @Operation(operationId = "retryPaybackPayout", summary = "실패한 페이백 지급 재요청")
    public RsData<Void> retry(@PathVariable Long paybackId) {
        paybackFacade.retryFailedPayback(paybackId);
        return new RsData<>("200-1", "페이백 지급을 다시 요청했습니다.");
    }
}
