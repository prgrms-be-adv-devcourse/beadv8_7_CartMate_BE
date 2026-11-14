package com.cartmate.cartmate.boundedContext.productService.product.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

// 재고를 이루는 상품옵션 하나. productTypeId는 아직 쓰지 않는다(옵션 종류는 상품옵션의 종류를 따른다)
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateProductOption {
    private Long productTypeId;
    private Long productOptionId;

    public static List<Long> optionIds(List<CreateProductOption> options) {
        if (options == null) {
            return List.of();
        }
        return options.stream().map(CreateProductOption::getProductOptionId).toList();
    }
}
