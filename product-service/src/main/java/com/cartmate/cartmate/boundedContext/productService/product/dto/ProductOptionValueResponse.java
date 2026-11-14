package com.cartmate.cartmate.boundedContext.productService.product.dto;

import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductOptionValue;

// 상품옵션 하나 (예: optionId 27, "265"). 등록 요청의 productOptionId로 그대로 쓴다
public record ProductOptionValueResponse(Long optionId, String name) {

    public static ProductOptionValueResponse from(ProductOptionValue value) {
        return new ProductOptionValueResponse(value.getId(), value.getName());
    }
}
