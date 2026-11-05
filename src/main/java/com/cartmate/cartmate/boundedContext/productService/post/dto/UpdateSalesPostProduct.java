package com.cartmate.cartmate.boundedContext.productService.post.dto;

import com.cartmate.cartmate.boundedContext.productService.shared.product.dto.UpdateProductOption;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class UpdateSalesPostProduct {
    private Long id;
    private Long categoryId;
    private String name;
    private String description;
    private Long basePrice;
    private List<UpdateSalesPostProductImage> productImageList;
    private List<UpdateProductOption> productOptionList;
}
