package com.cartmate.cartmate.shared.order;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 공동 주문(공용물품)의 상태. 참여자 결제 중 → 전원 결제 확정 / 취소
// 서비스 사이 계약 값(ARCH-8): 이름을 바꾸거나 지우면 받는 서비스가 깨진다. 값은 추가만 하고, 설명 없이는 추가할 수 없다.
@Getter
@RequiredArgsConstructor
public enum SharedGroupOrderStatus {
    PAYING("참여자 결제 중"),
    CONFIRMED("전원 결제 확정"),
    CANCELED("취소");

    private final String description;
}
