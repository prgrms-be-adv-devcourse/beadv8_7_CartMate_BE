package com.cartmate.cartmate.boundedContext.orderService.order.domain.personal;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BuyerCancelResult {
    CANCELED("취소 완료"),
    REQUESTED("취소 요청됨"),
    RESENT("취소 요청 재전송");

    private final String label;
}
