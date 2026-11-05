package com.cartmate.cartmate.boundedContext.productService.shared.product.event;

import com.cartmate.cartmate.boundedContext.productService.shared.product.dto.SyncProductRequest;

public record ProductUpdatedEvent(SyncProductRequest syncProductRequest) {
}
