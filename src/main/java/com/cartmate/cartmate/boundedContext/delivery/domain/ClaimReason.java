package com.cartmate.cartmate.boundedContext.delivery.domain;

public enum ClaimReason {
    SIZE_MISMATCH,  //사이즈 불일치
    SIMPLE_CHANGE,  //단순 변심
    PRODUCT_DEFECT, //상품 불량 및 파손
    WRONG_DELIVERY, //오배송 및 누락
    OTHER           //기타 사유
}
