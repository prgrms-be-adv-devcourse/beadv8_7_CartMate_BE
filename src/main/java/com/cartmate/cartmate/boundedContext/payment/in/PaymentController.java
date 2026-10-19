package com.cartmate.cartmate.boundedContext.payment.in;

import com.cartmate.cartmate.boundedContext.payment.app.PaymentFacade;
import com.cartmate.cartmate.boundedContext.payment.app.PaymentRetryService;
import com.cartmate.cartmate.boundedContext.payment.dto.PaymentPreviewResponse;
import com.cartmate.cartmate.boundedContext.payment.dto.PaymentResponse;
import com.cartmate.cartmate.boundedContext.payment.dto.SharedGroupPaymentRequest;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.global.security.SecurityUser;
import com.cartmate.cartmate.shared.order.dto.TeamDeliveryAddress;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentRetryService paymentRetryService;
    private final PaymentFacade paymentFacade;

    // ===== 개인 주문 =====
    @GetMapping("/orders/{orderId}/preview")
    public RsData<PaymentPreviewResponse> preview(@AuthenticationPrincipal SecurityUser user, @PathVariable Long orderId) {
        return new RsData<>("200-1", "결제 정보 조회 성공", paymentFacade.preview(user.memberId(), orderId));
    }

    @PostMapping("/orders/{orderId}")
    public RsData<PaymentResponse> payOrder(@AuthenticationPrincipal SecurityUser user, @PathVariable Long orderId) {
        return new RsData<>("200-1", "결제가 완료되었습니다.", paymentRetryService.payOrder(user.memberId(), orderId));
    }

    @GetMapping("/orders/{orderId}")
    public RsData<PaymentResponse> getByOrder(@AuthenticationPrincipal SecurityUser user, @PathVariable Long orderId) {
        return new RsData<>("200-1", "결제 조회 성공", paymentFacade.getByOrder(user.memberId(), orderId));
    }

    // ===== 공동물품 구매 =====
    @PostMapping("/shared-group-orders/{sharedGroupOrderId}")
    public RsData<PaymentResponse> paySharedGroupOrder(@AuthenticationPrincipal SecurityUser user,
                                                       @PathVariable Long sharedGroupOrderId,
                                                       @RequestBody(required = false) SharedGroupPaymentRequest request) {
        TeamDeliveryAddress address = request == null ? null : request.teamDeliveryAddress();
        return new RsData<>("200-1", "분담금 결제가 완료되었습니다.",
                paymentRetryService.paySharedGroupOrder(user.memberId(), sharedGroupOrderId, address));
    }

    @GetMapping("/shared-group-orders/{sharedGroupOrderId}")
    public RsData<PaymentResponse> getBySharedGroupOrder(@AuthenticationPrincipal SecurityUser user,
                                                         @PathVariable Long sharedGroupOrderId) {
        return new RsData<>("200-1", "결제 조회 성공",
                paymentFacade.getBySharedGroupOrder(user.memberId(), sharedGroupOrderId));
    }
}