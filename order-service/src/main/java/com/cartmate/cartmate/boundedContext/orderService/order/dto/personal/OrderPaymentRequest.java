package com.cartmate.cartmate.boundedContext.orderService.order.dto.personal;

// 개인 주문 결제 요청. 쿠폰을 쓰지 않으면 본문을 비워도 된다
public record OrderPaymentRequest(
        Long memberCouponId
) {
}
