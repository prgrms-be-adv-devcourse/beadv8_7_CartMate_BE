package com.cartmate.cartmate.shared.order;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SharedGroupOrderStatus {
    PAYING("결제 진행 중"),  // 참여자 결제 중
    CONFIRMED("결제 확정"),  // 전원 결제 확정
    CANCELED("취소");

    private final String label;
}
