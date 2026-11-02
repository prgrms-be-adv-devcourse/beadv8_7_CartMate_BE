package com.cartmate.cartmate.boundedContext.order.domain.groupshared;


public enum SharedGroupOrderCancelReason {

    DEADLINE_EXPIRED,

    OUT_OF_STOCK,

    CREATOR_CANCELED,

    SALES_STOPPED   // 판매글 판매 중지·숨김·삭제
}
