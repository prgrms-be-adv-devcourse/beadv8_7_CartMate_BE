package com.cartmate.cartmate.boundedContext.payout.domain;

// 정산 대상 주문상품이 속한 구매 유형. 개인 주문과 공동물품 주문은 주문상품 ID를 각자 채번하므로 유형까지 같이 봐야 같은 상품을 구분할 수 있다.
public enum PayoutPurchaseType {
    INDIVIDUAL_GROUP,
    SHARED_GROUP
}
