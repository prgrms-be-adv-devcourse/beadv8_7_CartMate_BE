package com.cartmate.cartmate.boundedContext.productService.product.dto;

import com.cartmate.cartmate.boundedContext.productService.product.domain.Category;

// 카테고리 하나 (상품 등록 화면의 카테고리 선택 목록)
public record CategoryResponse(Long categoryId, String name) {
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName());
    }
}
