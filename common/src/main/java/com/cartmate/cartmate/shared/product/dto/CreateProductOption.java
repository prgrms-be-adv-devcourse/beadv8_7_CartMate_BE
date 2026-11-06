package com.cartmate.cartmate.shared.product.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateProductOption {
    private Long productTypeId;
    private Long productOptionId;
}
