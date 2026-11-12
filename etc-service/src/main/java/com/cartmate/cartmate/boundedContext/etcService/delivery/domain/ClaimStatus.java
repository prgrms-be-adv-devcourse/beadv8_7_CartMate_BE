package com.cartmate.cartmate.boundedContext.etcService.delivery.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ClaimStatus {
    REQUESTED("접수"),  // 접수 (판매자 확인 전)
    APPROVED("승인"),   // 판매자 승인
    REJECTED("거절"),   // 판매자 거절
    COMPLETED("처리 완료");

    private final String label;
}
