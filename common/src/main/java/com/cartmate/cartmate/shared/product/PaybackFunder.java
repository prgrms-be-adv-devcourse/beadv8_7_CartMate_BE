package com.cartmate.cartmate.shared.product;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaybackFunder {
    ADMIN("플랫폼 부담"),   // 어드민이 건 조건
    SELLER("판매자 부담");  // 판매자가 건 조건

    private final String label;
}
