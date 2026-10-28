package com.cartmate.cartmate.boundedContext.delivery.domain;

public enum ClaimStatus {
    REQUESTED,  //접수 (판매자 확인 전)
    APPROVED,   //판매자 승인
    REJECTED,   //판매자 거절
    COMPLETED   //처리 완료
}
