package com.cartmate.cartmate.boundedContext.productService.product.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class DeleteProductListRequest {
    private List<SyncProduct> productList;
}
