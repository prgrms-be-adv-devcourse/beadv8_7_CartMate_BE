package com.cartmate.cartmate.boundedContext.productService.shared.post.event;

import com.cartmate.cartmate.boundedContext.productService.shared.post.dto.RollbackProductRequest;

public record PostApiCallFailedEvent(RollbackProductRequest rollbackProductRequest) {
}
