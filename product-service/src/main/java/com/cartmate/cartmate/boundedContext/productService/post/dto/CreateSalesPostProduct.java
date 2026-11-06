package com.cartmate.cartmate.boundedContext.productService.post.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateSalesPostProduct {
    private Long id;
    private Long salesPostId;
    private Long categoryId;
    private String name;
    private String description;
    private Long basePrice;
    private List<CreateSalesPostProductImage> productImageList;
    private List<CreateProductOption> productOptionList;

    public CreateProduct toCreateProduct(Long sellerId) {
        List<CreateProductImage> productImages = productImageList.stream()
                .map(image -> image.toCreateProductImage(sellerId))
                .toList();

        return new CreateProduct(
                id,
                sellerId,
                salesPostId,
                categoryId,
                name,
                description,
                basePrice,
                productImages,
                productOptionList
        );
    }
}
