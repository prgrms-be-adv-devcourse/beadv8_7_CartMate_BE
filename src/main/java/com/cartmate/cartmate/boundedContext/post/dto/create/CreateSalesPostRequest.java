package com.cartmate.cartmate.boundedContext.post.dto.create;

import com.cartmate.cartmate.shared.product.dto.create.CreateProduct;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateSalesPostRequest {
    private int id;
    private int sellerId;
    private String title;
    private String content;
    private List<CreateSalesPostProduct> productList;

    public List<CreateProduct> toCreateProductList() {
        return productList.stream()
                .map(product -> product.toCreateProduct(sellerId))
                .toList();
    }
}
