package com.cartmate.cartmate.shared.product.dto.create;

import com.cartmate.cartmate.boundedContext.post.dto.create.CreateSalesPostProductImage;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateProduct {
    private int id;
    private int sellerId;
    private int salesPostId;
    private int categoryId;
    private String name;
    private String description;
    private String basePrice;
    private List<CreateProductImage> productImageList;

    public void assignProductId(int id) {
        this.id = id;
    }
}
