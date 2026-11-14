package com.cartmate.cartmate.boundedContext.orderService.order.domain.salesstop;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SalesStopCancelRequestStatus {
    REQUESTED("취소 요청 중", "배송 서버 결과 기다리는 중"),
    CANCELED("취소 확정"),
    REJECTED("취소 거절", "배송이 발송돼 취소 안 함");

    private final String label;
    private final String description;

    // 라벨만으로 충분하면 라벨을 설명으로도 쓴다.
    SalesStopCancelRequestStatus(String label) {
        this(label, label);
    }
}
