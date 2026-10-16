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
    private int id;
    private int salesPostId;
    private int categoryId;
    private String name;
    private String description;
    private String basePrice;
    private List<CreateSalesPostProductImage> productImageList;

    public CreateProduct toCreateProduct(int sellerId) {
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
