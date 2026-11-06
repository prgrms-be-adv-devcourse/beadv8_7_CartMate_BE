package com.cartmate.cartmate.boundedContext.productService.post.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class RestoreProductsRequest {
    private List<RestoreProduct> productList;
}
