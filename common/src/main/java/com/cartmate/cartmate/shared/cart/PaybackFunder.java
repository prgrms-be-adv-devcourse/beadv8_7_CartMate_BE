package com.cartmate.cartmate.shared.cart;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaybackFunder {
    ADMIN("어드민이 건 조건, 플랫폼 부담"),
    SELLER("판매자가 건 조건, 판매자 부담");

    private final String description;
}
