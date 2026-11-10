package com.cartmate.cartmate.boundedContext.productService.product.dto;

import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductStatus;
import jakarta.validation.constraints.NotNull;

// 판매글 단위로 상품 판매 상태를 바꾼다: ON_SALE(판매 재개) / PAUSED(판매자 내림) / SUSPENDED(관리자 숨김)
// post가 보내는 요청(post 쪽 ProductStatusChangeRequest)과 JSON 모양만 같다
public record ProductStatusChangeRequest(@NotNull ProductStatus status) {
}
