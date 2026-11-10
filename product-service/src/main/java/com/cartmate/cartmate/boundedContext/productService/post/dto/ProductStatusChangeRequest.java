package com.cartmate.cartmate.boundedContext.productService.post.dto;

import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostProductStatus;

// 판매글 단위로 상품 판매 상태 변경을 product에 요청한다: ON_SALE(판매 재개) / PAUSED(판매자 내림) / SUSPENDED(관리자 숨김)
// product가 받는 요청(product 쪽 ProductStatusChangeRequest)과 JSON 모양만 같다
public record ProductStatusChangeRequest(SalesPostProductStatus status) {
}
