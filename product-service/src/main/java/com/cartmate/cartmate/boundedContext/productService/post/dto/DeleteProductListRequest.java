package com.cartmate.cartmate.boundedContext.productService.post.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DeleteProductListRequest {
    private List<SyncProduct> productList;
}
