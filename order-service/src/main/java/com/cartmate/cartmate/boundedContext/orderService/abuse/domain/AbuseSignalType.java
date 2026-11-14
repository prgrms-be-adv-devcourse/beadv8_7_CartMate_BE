package com.cartmate.cartmate.boundedContext.orderService.abuse.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AbuseSignalType {
    SHARED_ORDER_PAYMENT_EXPIRED("공동 주문 결제 기한 만료"),
    SHARED_PURCHASE_CREATOR_CANCELED("공동구매 개설자 취소"),
    INDIVIDUAL_ORDER_PAYMENT_EXPIRED("개인 주문 결제 기한 만료"),
    SHARED_PURCHASE_LEFT("공동구매 이탈"),
    SHARED_PURCHASE_CONCURRENT_JOIN("공동구매 동시 다수 참여");

    private final String label;
}
