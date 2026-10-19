package com.cartmate.cartmate.boundedContext.post.dto;

import com.cartmate.cartmate.boundedContext.product.domain.ProductStatus;
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
    private ProductStatus status;
    private List<UpdateSalesPostProductImage> productImageList;
}
