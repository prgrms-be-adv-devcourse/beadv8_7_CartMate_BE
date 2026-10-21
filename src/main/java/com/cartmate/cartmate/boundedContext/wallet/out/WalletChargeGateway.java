package com.cartmate.cartmate.boundedContext.wallet.out;

// 충전 결제를 승인하는 외부 결제사와 닿는 곳. 결제사가 무엇인지는 구현이 알고, 호출하는 쪽은 이 인터페이스만 안다.
// 실패하면 ChargeGatewayException을 던지고, 호출하는 쪽은 failureType으로 확정 실패와 모호한 실패를 가른다.
public interface WalletChargeGateway {

    // 결제창에서 인증이 끝난 결제를 승인한다. 같은 idempotencyKey로 다시 보내도 결제사에서 한 번만 처리된다.
    ChargeGatewayPayment confirm(String paymentKey, String orderNumber, Long amount, String idempotencyKey);

    // 결제키로 결제사의 현재 결제 상태를 조회한다. 승인 응답을 받지 못했을 때 실제 처리 여부를 확인하는 데 쓴다.
    ChargeGatewayPayment fetch(String paymentKey);
}
