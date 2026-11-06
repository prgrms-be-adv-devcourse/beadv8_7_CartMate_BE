package com.cartmate.cartmate.shared.cart;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum IndividualGroupPurchaseOrigin {
    MEMBER("회원이 연 공동구매"),
    ADMIN_DEMAND("어드민이 장바구니 수요로 연 공동구매");

    private final String description;
}
