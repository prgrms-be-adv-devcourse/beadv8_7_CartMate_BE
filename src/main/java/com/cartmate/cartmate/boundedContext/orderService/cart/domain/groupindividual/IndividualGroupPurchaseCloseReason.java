package com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual;

public enum IndividualGroupPurchaseCloseReason {
    CREATOR,        // 개설자가 마감
    ADMIN,          // 다른 어드민이 마감 (어드민 공동구매만)
    DEADLINE,       // 마감 시각이 지나 시스템이 마감 (어드민 공동구매만)
    SALES_STOPPED   // 판매 중지로 상품이 모두 빠짐
}
