package com.cartmate.cartmate.boundedContext.orderService.order.in.personal;

import com.cartmate.cartmate.boundedContext.orderService.order.app.OrderPaymentRetryService;
import com.cartmate.cartmate.boundedContext.orderService.order.app.personal.OrderFacade;
import com.cartmate.cartmate.boundedContext.orderService.order.dto.personal.*;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.global.security.SecurityUser;
import com.cartmate.cartmate.boundedContext.paymentService.shared.payment.dto.PaymentResult;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderFacade orderFacade;
    private final OrderPaymentRetryService orderPaymentRetryService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RsData<OrderCreateResponse> createPersonal(
            @AuthenticationPrincipal SecurityUser user,
            @Valid @RequestBody OrderCreateRequest request
    ) {
        return new RsData<>("201-1", "주문을 만들었습니다.", orderFacade.createPersonal(user.memberId(), request));
    }

    @PostMapping("/individual-group")
    @ResponseStatus(HttpStatus.CREATED)
    public RsData<OrderCreateResponse> createIndividualGroup(
            @AuthenticationPrincipal SecurityUser user,
            @Valid @RequestBody IndividualGroupOrderCreateRequest request
    ) {
        return new RsData<>("201-1", "공동구매 상품을 주문했습니다.", orderFacade.createIndividualGroup(user.memberId(), request));
    }

    @GetMapping("/me")
    public RsData<List<OrderSummaryResponse>> getMyList(@AuthenticationPrincipal SecurityUser user) {
        return new RsData<>("200-1", "내 주문 목록 조회 성공했습니다.", orderFacade.getMyList(user.memberId()));
    }

    @GetMapping("/{id}")
    public RsData<OrderDetailResponse> getDetail(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable Long id
    ) {
        return new RsData<>("200-1", "주문 조회 성공했습니다.", orderFacade.getDetail(user.memberId(), id));
    }

    @PostMapping("/{id}/cancel")
    public RsData<Void> cancel(
            @AuthenticationPrincipal SecurityUser user,
            @PathVariable Long id
    ) {
        orderFacade.cancelByBuyer(user.memberId(), id);
        return new RsData<>("200-1", "주문을 취소했습니다.");
    }

    // ===== 결제 (주문이 지휘, 예치금 차감은 결제 모듈) =====
    // 예전 POST /api/payments/orders/{id}, GET /api/payments/orders/{id}/preview 를 주문으로 옮김.
    // operationId는 그대로 두어 프론트 생성 훅 이름(usePayOrder, usePreview)이 바뀌지 않게 한다.
    @PostMapping("/{orderId}/payment")
    @Operation(operationId = "payOrder", summary = "개인 주문 예치금 결제")
    public RsData<PaymentResult> pay(@AuthenticationPrincipal SecurityUser user, @PathVariable Long orderId) {
        return new RsData<>("200-1", "결제가 완료되었습니다.", orderPaymentRetryService.payOrder(user.memberId(), orderId));
    }
    @GetMapping("/{orderId}/payment-preview")
    @Operation(operationId = "preview", summary = "개인 주문 결제 미리보기 (금액·결제 가능 여부·예치금 잔액)")
    public RsData<OrderPaymentPreviewResponse> paymentPreview(@AuthenticationPrincipal SecurityUser user,
    @PathVariable Long orderId) {
        return new RsData<>("200-1", "결제 정보 조회 성공", orderFacade.getPaymentPreview(user.memberId(), orderId));
    }
}
