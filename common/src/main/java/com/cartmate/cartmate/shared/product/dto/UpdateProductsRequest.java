package com.cartmate.cartmate.shared.product.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class UpdateProductsRequest {
    private Long id;
    private List<UpdateProduct> productList;
}
