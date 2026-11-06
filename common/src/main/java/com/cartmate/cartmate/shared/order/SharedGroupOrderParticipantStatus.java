package com.cartmate.cartmate.shared.order;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 공동 주문 참여자 한 명의 분담금 결제 상태
// 서비스 사이 계약 값(ARCH-8): 이름을 바꾸거나 지우면 받는 서비스가 깨진다. 값은 추가만 하고, 설명 없이는 추가할 수 없다.
@Getter
@RequiredArgsConstructor
public enum SharedGroupOrderParticipantStatus {
    UNPAID("미결제"),
    PAID("결제 완료");

    private final String description;
}
