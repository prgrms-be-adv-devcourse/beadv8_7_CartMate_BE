package com.cartmate.cartmate.boundedContext.productService.post.dto;

import java.util.List;

/**
 * 상세 화면 선택 목록: 옵션 종류 하나와, 이 상품의 재고에 쓰인 그 종류의 상품옵션(중복 없이).
 * 예) { typeId 3, "신발 사이즈", [255, 265] }
 */
public record SelectSalesPostOptionGroup(Long typeId, String typeName, List<SelectSalesPostOptionValue> options) {
}
