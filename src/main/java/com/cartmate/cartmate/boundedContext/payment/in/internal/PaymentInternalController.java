package com.cartmate.cartmate.boundedContext.payment.in.internal;

import com.cartmate.cartmate.boundedContext.payment.app.PaymentFacade;
import com.cartmate.cartmate.boundedContext.payment.exception.PaymentErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.rsData.RsData;
import com.cartmate.cartmate.shared.payment.dto.PaymentChargeRequest;
import com.cartmate.cartmate.shared.payment.dto.PaymentOrderType;
import com.cartmate.cartmate.shared.payment.dto.PaymentResult;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 결제 서버 내부 API. 다른 서버(주문)만 부른다 — 외부 요청은 Gateway가 막고, 앱은 내부 API 키로 한 번 더 확인한다.
 * 프론트 API 문서·생성 코드에 나오지 않게 숨긴다.
 */
@Hidden
@RestController
@RequestMapping("/internal/payments")
@RequiredArgsConstructor
public class PaymentInternalController {

    private final PaymentFacade paymentFacade;

    // (구분·주문·회원) 키로 한 번만 차감. 같은 키로 다시 오면 기존 결제를 돌려준다.
    @PostMapping
    public RsData<PaymentResult> charge(@RequestBody PaymentChargeRequest request) {
        return new RsData<>("200-1", "차감 완료",
                paymentFacade.charge(request.orderType(), request.orderId(), request.memberId(), request.amount()));
    }

    // 결제 여부 확인 (응답을 못 받았을 때 주문이 확인). 없으면 404
    @GetMapping
    public RsData<PaymentResult> find(@RequestParam PaymentOrderType orderType,
                                      @RequestParam Long orderId,
                                      @RequestParam Long memberId) {
        return paymentFacade.findPayment(orderType, orderId, memberId)
                .map(result -> new RsData<>("200-1", "결제 조회", result))
                .orElseThrow(() -> new ServiceException(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }

    // 차감 후 주문 반영이 실패했을 때 되돌리기 (여러 번 와도 한 번만)
    @PostMapping("/compensate")
    public RsData<Void> compensate(@RequestParam PaymentOrderType orderType,
                                   @RequestParam Long orderId,
                                   @RequestParam Long memberId) {
        paymentFacade.compensateCharge(orderType, orderId, memberId);
        return new RsData<>("200-1", "결제 되돌림");
    }

    @GetMapping("/wallet-balance")
    public RsData<Long> walletBalance(@RequestParam Long memberId) {
        return new RsData<>("200-1", "예치금 잔액", paymentFacade.getWalletBalance(memberId));
    }
}