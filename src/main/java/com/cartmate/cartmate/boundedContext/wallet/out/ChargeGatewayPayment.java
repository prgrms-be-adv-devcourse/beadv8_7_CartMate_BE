package com.cartmate.cartmate.boundedContext.wallet.out;

// 결제사가 알려준 결제 한 건. 승인된 금액(amount)은 호출하는 쪽이 충전 주문 금액과 대조한다.
public record ChargeGatewayPayment(
        String paymentKey,
        String orderNumber, // 결제사의 orderId. 충전 주문의 주문번호와 같아야 한다.
        Long amount,
        Status status
) {
    public enum Status {
        DONE,
        IN_PROGRESS,
        CANCELED,
        FAILED,
        UNKNOWN
    }
}
