package com.cartmate.cartmate.boundedContext.productService.product.dto;

import com.cartmate.cartmate.boundedContext.productService.product.domain.ProductStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class RestoreProduct {
    private Long id;
    private Long categoryId;
    private String name;
    private String description;
    private Long basePrice;
    private List<RestoreProductImage> productImageList;
    private List<RestoreInventory> inventoryList;
    private ProductStatus status;   // 수정 전 판매 상태 (복구 때 이 상태로 되돌린다)
}
