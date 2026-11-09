package com.cartmate.cartmate.boundedContext.orderService.order.domain.salesstop;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SalesStopCancelRequestStatus {
    REQUESTED("배송 서버 결과 기다리는 중"),
    CANCELED("취소 확정"),
    REJECTED("배송이 발송돼 취소 안 함");

    private final String description;
}
