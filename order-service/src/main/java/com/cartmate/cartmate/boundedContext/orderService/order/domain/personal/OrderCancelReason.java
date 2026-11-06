package com.cartmate.cartmate.boundedContext.orderService.order.domain.personal;

public enum OrderCancelReason {
    BUYER_CANCELED,
    PAYMENT_EXPIRED,
    OUT_OF_STOCK,
    SALES_STOPPED,   // 판매글 판매 중지·숨김·삭제
    GROUP_PURCHASE_CLOSED   // 개인물품 공동구매가 닫힘(결제 마감 = 판매 마감, ARCH-17)
}
