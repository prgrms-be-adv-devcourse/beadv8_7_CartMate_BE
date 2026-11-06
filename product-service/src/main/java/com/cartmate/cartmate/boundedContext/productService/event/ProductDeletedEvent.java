package com.cartmate.cartmate.boundedContext.productService.event;

import com.cartmate.cartmate.shared.post.dto.DeleteProductRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ProductDeletedEvent {
    private final DeleteProductRequest request;
}
