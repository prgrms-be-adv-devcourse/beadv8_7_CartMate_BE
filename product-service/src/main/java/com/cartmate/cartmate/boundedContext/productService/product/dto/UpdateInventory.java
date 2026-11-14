package com.cartmate.cartmate.boundedContext.productService.product.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

// 수정 요청의 재고 하나. id가 없으면 새 재고(optionList로 조합을 만든다),
// id가 있으면 기존 재고의 추가금액 · 재고 수량만 바꾼다(조합은 바꾸지 않으므로 optionList는 보지 않는다)
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateInventory {
    private Long id;
    private List<CreateProductOption> optionList;
    private Long additionalPrice;
    private Integer stockQuantity;
}
