package com.cartmate.cartmate.boundedContext.payment.in;

import com.cartmate.cartmate.boundedContext.payment.app.PaymentFacade;
import com.cartmate.cartmate.boundedContext.payment.app.PaymentRetryService;
import com.cartmate.cartmate.boundedContext.payment.dto.PaymentPreviewResponse;
import com.cartmate.cartmate.boundedContext.payment.dto.PaymentResponse;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.global.security.SecurityUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentRetryService paymentRetryService;
    private final PaymentFacade paymentFacade;

    // 개인 주문 결제 (예치금 즉시 차감)
    @PostMapping("/orders/{orderId}")
    public RsData<PaymentResponse> payOrder(@AuthenticationPrincipal SecurityUser user, @PathVariable Long orderId) {
        return new RsData<>("200-1", "결제가 완료되었습니다.", paymentRetryService.payOrder(user.memberId(), orderId));
    }

    @GetMapping("/orders/{orderId}")
    public RsData<PaymentResponse> getByOrder(@AuthenticationPrincipal SecurityUser user, @PathVariable Long orderId) {
        return new RsData<>("200-1", "결제 조회 성공", paymentFacade.getByOrder(user.memberId(), orderId));
    }

    // 결제 취소 (예치금 환불)
    @PostMapping("/{paymentId}/cancel")
    public RsData<PaymentResponse> cancel(@AuthenticationPrincipal SecurityUser user, @PathVariable Long paymentId) {
        return new RsData<>("200-1", "결제가 취소되었습니다.", paymentRetryService.cancel(user.memberId(), paymentId));
    }

    @GetMapping("/orders/{orderId}/preview")
    public RsData<PaymentPreviewResponse> preview(@AuthenticationPrincipal SecurityUser user, @PathVariable Long orderId) {
        return new RsData<>("200-1", "결제 정보 조회 성공", paymentFacade.preview(user.memberId(), orderId));
    }
}