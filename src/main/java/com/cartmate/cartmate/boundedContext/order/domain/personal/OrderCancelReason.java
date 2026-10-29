package com.cartmate.cartmate.boundedContext.order.domain.personal;

public enum OrderCancelReason {
    BUYER_CANCELED,
    PAYMENT_EXPIRED,
    OUT_OF_STOCK,
    SALES_STOPPED   // 판매글 판매 중지·숨김·삭제
}
