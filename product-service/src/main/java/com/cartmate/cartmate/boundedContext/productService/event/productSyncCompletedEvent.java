package com.cartmate.cartmate.boundedContext.productService.event;

import com.cartmate.cartmate.boundedContext.productService.post.dto.ProductSyncUpdateRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class productSyncCompletedEvent {
    private ProductSyncUpdateRequest request;
}
