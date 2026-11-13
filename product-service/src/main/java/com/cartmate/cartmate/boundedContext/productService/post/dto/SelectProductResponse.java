package com.cartmate.cartmate.boundedContext.productService.post.dto;

import com.cartmate.cartmate.boundedContext.productService.post.domain.SalesPostProductStatus;
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
    private SalesPostProductStatus status;
    private List<SelectProductImage> productImageList;
    private List<SelectProductOption> productOptionList;
    private List<SelectInventory> inventoryList;
}
