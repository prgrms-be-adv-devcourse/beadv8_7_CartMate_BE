package com.cartmate.cartmate.boundedContext.productService.product.dto;

import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class SelectProductResponse {
    private Long id;
    private Long salesPostId;
    private Long categoryId;
    private String name;
    private String description;
    private Long basePrice;
    private ProductStatus status;
    private List<SelectProductImage> productImageList;
    private List<SelectProductOption> productOptionList;
    private List<SelectInventory> inventoryList;
}
