package com.cartmate.cartmate.boundedContext.paymentService.payment.in;

import com.cartmate.cartmate.boundedContext.paymentService.payment.app.PaymentFacade;
import com.cartmate.cartmate.boundedContext.paymentService.payment.dto.PaymentResponse;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.global.security.SecurityUser;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentFacade paymentFacade;

    // 결제(차감)는 주문 쪽 API가 지휘한다: POST /api/orders/{id}/payment, POST /api/group-orders/shared/{id}/payment
    // 결제 서버에는 결제 내역 조회만 남는다.
    @GetMapping("/orders/{orderId}")
    @Operation(operationId = "getByOrder", summary = "개인 주문 결제 내역")
    public RsData<PaymentResponse> getByOrder(@AuthenticationPrincipal SecurityUser user, @PathVariable Long orderId) {
        return new RsData<>("200-1", "결제 조회 성공", paymentFacade.getByOrder(user.memberId(), orderId));
    }

    @GetMapping("/shared-group-orders/{sharedGroupOrderId}")
    @Operation(operationId = "getBySharedGroupOrder", summary = "공동 주문 분담금 결제 내역")
    public RsData<PaymentResponse> getBySharedGroupOrder(@AuthenticationPrincipal SecurityUser user,
                                                         @PathVariable Long sharedGroupOrderId) {
        return new RsData<>("200-1", "결제 조회 성공",
                paymentFacade.getBySharedGroupOrder(user.memberId(), sharedGroupOrderId));
    }
}