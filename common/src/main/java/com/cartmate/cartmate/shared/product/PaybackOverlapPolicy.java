package com.cartmate.cartmate.shared.product;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaybackOverlapPolicy {
    BOTH("모두 지급", "어드민 · 판매자 조건을 모두 지급"),
    LARGER("큰 금액만 지급", "달성한 조건 중 개당 금액이 큰 쪽만 지급"),
    ADMIN_ONLY("어드민 조건만 지급"),
    SELLER_ONLY("판매자 조건만 지급");

    private final String label;
    private final String description;

    // 라벨만으로 충분하면 라벨을 설명으로도 쓴다.
    PaybackOverlapPolicy(String label) {
        this(label, label);
    }
}
