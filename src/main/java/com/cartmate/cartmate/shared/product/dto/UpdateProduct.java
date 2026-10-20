package com.cartmate.cartmate.shared.product.dto;

import com.cartmate.cartmate.boundedContext.product.domain.ProductStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class UpdateProduct {
    private Long id;
    private Long categoryId;
    private String name;
    private String description;
    private Long basePrice;
    private List<UpdateProductImage> productImageList;
    private List<UpdateProductOption> productOptionList;
}
