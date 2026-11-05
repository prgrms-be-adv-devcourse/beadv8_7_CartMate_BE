package com.cartmate.cartmate.boundedContext.productService.shared.post.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class productSyncCompletedEvent {
    private ProductSyncUpdateRequest request;
}
