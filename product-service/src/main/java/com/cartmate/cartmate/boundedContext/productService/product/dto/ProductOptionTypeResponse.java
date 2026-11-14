package com.cartmate.cartmate.boundedContext.productService.product.dto;

import java.util.List;

// 옵션 종류 하나와 그 종류의 상품옵션 목록 (예: typeId 3 "신발 사이즈" → 220 ~ 300)
public record ProductOptionTypeResponse(Long typeId, String typeName, List<ProductOptionValueResponse> options) {
}
