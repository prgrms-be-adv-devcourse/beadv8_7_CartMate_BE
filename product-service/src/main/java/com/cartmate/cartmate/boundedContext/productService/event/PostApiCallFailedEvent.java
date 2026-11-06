package com.cartmate.cartmate.boundedContext.productService.event;

import com.cartmate.cartmate.boundedContext.productService.post.dto.RollbackProductRequest;

public record PostApiCallFailedEvent(RollbackProductRequest rollbackProductRequest) {
}
