package com.cartmate.cartmate.shared.cart;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 개인물품 공동구매를 누가 열었나
// 서비스 사이 계약 값(ARCH-8): 이름을 바꾸거나 지우면 받는 서비스가 깨진다. 값은 추가만 하고, 설명 없이는 추가할 수 없다.
@Getter
@RequiredArgsConstructor
public enum IndividualGroupPurchaseOrigin {
    MEMBER("회원이 연 공동구매"),
    ADMIN_DEMAND("어드민이 장바구니 수요로 연 공동구매");

    private final String description;
}
