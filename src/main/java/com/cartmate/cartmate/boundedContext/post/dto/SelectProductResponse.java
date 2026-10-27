package com.cartmate.cartmate.boundedContext.post.dto;

import com.cartmate.cartmate.boundedContext.product.domain.ProductStatus;
import com.cartmate.cartmate.shared.product.dto.CreateProductOption;
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
}
