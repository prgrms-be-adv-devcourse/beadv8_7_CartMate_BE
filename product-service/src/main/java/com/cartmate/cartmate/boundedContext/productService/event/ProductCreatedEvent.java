package com.cartmate.cartmate.boundedContext.productService.event;

import com.cartmate.cartmate.boundedContext.productService.product.dto.SyncProductRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ProductCreatedEvent {
    private SyncProductRequest request;
}
