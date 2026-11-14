package com.cartmate.cartmate.boundedContext.productService.product.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class SelectProductOption {
    private Long id;
    private Long productTypeId;
    private Long productOptionId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String productTypeName;     // 옵션 종류 이름 (예: 신발 사이즈)
    private String productOptionName;   // 상품옵션 이름 (예: 265)
}
