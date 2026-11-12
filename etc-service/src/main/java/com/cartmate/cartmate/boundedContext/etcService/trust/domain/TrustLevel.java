package com.cartmate.cartmate.boundedContext.etcService.trust.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TrustLevel {
    NEW("신규"),  // 거래 표본 부족
    LOW("낮음"),
    NORMAL("보통"),
    HIGH("높음");

    private final String label;
}
