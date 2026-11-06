package com.cartmate.cartmate.shared.order;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SharedGroupOrderStatus {
    PAYING("참여자 결제 중"),
    CONFIRMED("전원 결제 확정"),
    CANCELED("취소");

    private final String description;
}
