package com.cartmate.cartmate.boundedContext.productService.product.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProductUpdateStatus {
    NEW("신규"),
    PENDING("수정 중"),
    COMPLETED("수정 완료"),
    RECOVERING("복구 중"),
    RECOVERED("복구 완료"),
    RECOVERY_FAILED("복구 실패");

    private final String label;
}