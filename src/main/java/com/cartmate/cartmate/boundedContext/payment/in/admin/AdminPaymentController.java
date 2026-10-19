package com.cartmate.cartmate.boundedContext.payment.in.admin;

import com.cartmate.cartmate.boundedContext.payment.app.PaymentFacade;
import com.cartmate.cartmate.boundedContext.payment.dto.PaymentRefundRequest;
import com.cartmate.cartmate.boundedContext.payment.dto.PaymentResponse;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.global.security.SecurityUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/payments")
@RequiredArgsConstructor
public class AdminPaymentController {
    private final PaymentFacade paymentFacade;

    @PostMapping("/{paymentId}/refunds")
    public RsData<PaymentResponse> refund(@AuthenticationPrincipal SecurityUser admin,
                                          @PathVariable Long paymentId,
                                          @Valid @RequestBody PaymentRefundRequest request) {
        return new RsData<>("200-1", "환불이 처리되었습니다.",
                paymentFacade.refundByAdmin(admin.memberId(), paymentId, request.amount(), request.memo()));
    }
}