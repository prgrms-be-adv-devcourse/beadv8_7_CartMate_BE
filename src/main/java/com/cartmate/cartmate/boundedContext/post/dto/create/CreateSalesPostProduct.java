package com.cartmate.cartmate.boundedContext.post.dto.create;

import com.cartmate.cartmate.shared.product.dto.create.CreateProduct;
import com.cartmate.cartmate.shared.product.dto.create.CreateProductImage;
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
