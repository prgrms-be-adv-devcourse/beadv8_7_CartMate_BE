package com.cartmate.cartmate.boundedContext.post.dto;

import com.cartmate.cartmate.shared.product.dto.CreateProduct;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateSalesPostRequest {
    private Long id;
    private Long sellerId;
    private String title;
    private String content;
    private List<CreateSalesPostProduct> productList;

    public List<CreateProduct> toCreateProductList() {
        return productList.stream()
                .map(product -> product.toCreateProduct(sellerId))
                .toList();
    }
}
