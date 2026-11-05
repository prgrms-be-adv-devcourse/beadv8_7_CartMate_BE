package com.cartmate.cartmate.boundedContext.productService.shared.post.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ProductSyncUpdateRequest {
    private List<Long> successList;
}
