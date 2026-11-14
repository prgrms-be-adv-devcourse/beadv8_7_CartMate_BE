package com.cartmate.cartmate.boundedContext.productService.product.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

// 등록 요청의 재고 하나: 상품옵션 조합 + 추가금액 + 재고 수량 (예: 265 · 블랙, 0원, 10개)
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateInventory {
    private List<CreateProductOption> optionList;   // 비어 있으면 옵션 없는 상품의 기본 재고
    private Long additionalPrice;
    private Integer stockQuantity;
}
