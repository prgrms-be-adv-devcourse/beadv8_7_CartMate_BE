package com.cartmate.cartmate.boundedContext.etcService.trust.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TrustHistoryType {
    PURCHASE_CONFIRMED("구매 확정"),
    FAULT_CLAIM("판매자 귀책 클레임");  // 판매자 귀책 클레임 승인

    private final String label;
}
