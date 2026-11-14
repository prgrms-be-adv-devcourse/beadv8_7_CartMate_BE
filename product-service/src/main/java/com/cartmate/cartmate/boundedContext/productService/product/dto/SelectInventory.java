package com.cartmate.cartmate.boundedContext.productService.product.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SelectInventory {
    private Long id;
    private String skuName;
    private Long appliedPrice;
    private Long additionalPrice;
    private Integer stockQuantity;
    private Integer reservedQuantity;
    private List<SelectProductOption> optionList;   // 이 재고를 이루는 선택된 옵션
}
