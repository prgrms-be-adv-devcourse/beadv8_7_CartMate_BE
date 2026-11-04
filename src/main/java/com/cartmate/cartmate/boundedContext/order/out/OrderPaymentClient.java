package com.cartmate.cartmate.boundedContext.order.out;

import com.cartmate.cartmate.shared.payment.dto.PaymentOrderType;
import com.cartmate.cartmate.shared.payment.dto.PaymentResult;

import java.util.Optional;

/**
 * 주문이 결제를 부르는 창구. 결제 흐름은 주문이 지휘하고, 결제는 "키로 한 번만 차감"만 한다.
 * 지금은 같은 앱의 결제 Facade를 부르고, 결제 서버를 분리하면 구현만 HTTP로 바꾼다.
 */
public interface OrderPaymentClient {

    // (구분·주문·회원) 키로 한 번만 차감. 같은 키로 다시 부르면 이미 한 결제를 돌려준다.
    PaymentResult charge(PaymentOrderType orderType, Long orderId, Long memberId, long amount);

    // 결제 여부 확인 (응답을 못 받았을 때, 이미 결제했는지 볼 때)
    Optional<PaymentResult> findPayment(PaymentOrderType orderType, Long orderId, Long memberId);

    long getWalletBalance(Long memberId);
}