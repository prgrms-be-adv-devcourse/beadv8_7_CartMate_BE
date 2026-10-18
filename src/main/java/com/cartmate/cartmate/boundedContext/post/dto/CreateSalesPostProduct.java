package com.cartmate.cartmate.boundedContext.post.dto;

import com.cartmate.cartmate.shared.product.dto.CreateProduct;
import com.cartmate.cartmate.shared.product.dto.CreateProductImage;
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
                productImages
        );
    }
}
