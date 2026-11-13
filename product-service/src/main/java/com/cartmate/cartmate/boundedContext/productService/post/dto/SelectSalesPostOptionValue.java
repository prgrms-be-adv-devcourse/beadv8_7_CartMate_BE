package com.cartmate.cartmate.boundedContext.productService.post.dto;

// 선택 목록의 상품옵션 하나 (예: optionId 27, "265")
public record SelectSalesPostOptionValue(Long optionId, String name) {
}
