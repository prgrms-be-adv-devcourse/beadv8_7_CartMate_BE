package com.cartmate.cartmate.boundedContext.productService.event;

import com.cartmate.cartmate.boundedContext.productService.product.dto.SyncProductRequest;

public record ProductUpdatedEvent(SyncProductRequest syncProductRequest) {
}
