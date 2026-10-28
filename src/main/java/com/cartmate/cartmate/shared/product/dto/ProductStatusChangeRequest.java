package com.cartmate.cartmate.shared.product.dto;

import com.cartmate.cartmate.boundedContext.product.domain.ProductStatus;
import jakarta.validation.constraints.NotNull;

// 판매글 단위로 상품 판매 상태를 바꾼다: ON_SALE(판매 재개) / PAUSED(판매자 내림) / SUSPENDED(관리자 숨김)
public record ProductStatusChangeRequest(@NotNull ProductStatus status) {
}
