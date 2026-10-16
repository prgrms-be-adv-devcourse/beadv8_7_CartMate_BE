package com.cartmate.cartmate.shared.product.dto.create;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateProduct {
    private Long id;
    private Long sellerId;
    private Long salesPostId;
    private int categoryId;
    private String name;
    private String description;
    private Long basePrice;
    private List<CreateProductImage> productImageList;

    public void assignProductId(Long id) {
        this.id = id;
    }
}
