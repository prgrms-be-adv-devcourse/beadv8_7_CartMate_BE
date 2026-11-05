package com.cartmate.cartmate.boundedContext.orderService.order.out;

import com.cartmate.cartmate.shared.payment.dto.PaymentOrderType;
import com.cartmate.cartmate.shared.payment.dto.PaymentResult;

import java.util.Optional;

/**
 * 주문이 결제를 부르는 창구. 결제 흐름은 주문이 지휘하고, 결제는 "키로 한 번만 차감"만 한다.
 * 구현은 설정 cartmate.payment.client 로 고른다.
 *   local (기본): 같은 앱의 결제 Facade 호출 — 차감과 주문 반영이 한 트랜잭션
 *   http        : 결제 서버 내부 API 호출 — 차감과 주문 반영이 다른 트랜잭션 (서버 분리 후와 같음)
 */
public interface OrderPaymentClient {

    // (구분·주문·회원) 키로 한 번만 차감. 같은 키로 다시 부르면 이미 한 결제를 돌려준다.
    PaymentResult charge(PaymentOrderType orderType, Long orderId, Long memberId, long amount);

    // 결제 여부 확인 (응답을 못 받았을 때, 이미 결제했는지 볼 때)
    Optional<PaymentResult> findPayment(PaymentOrderType orderType, Long orderId, Long memberId);

    long getWalletBalance(Long memberId);

    // 차감은 됐는데 주문 반영이 거절됐을 때 되돌리기 (여러 번 불러도 한 번만).
    // 실패해도 예외를 던지지 않는다 — 호출하는 쪽의 원래 오류(왜 거절됐는지)를 가리지 않기 위해. 실패는 구현이 로그로 남긴다.
    // 같은 앱(local)에서는 차감과 반영이 한 트랜잭션이라 롤백으로 충분해 아무것도 안 한다.
    void compensateCharge(PaymentOrderType orderType, Long orderId, Long memberId);
}