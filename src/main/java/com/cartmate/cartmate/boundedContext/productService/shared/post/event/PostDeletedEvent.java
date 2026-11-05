package com.cartmate.cartmate.boundedContext.productService.shared.post.event;

import com.cartmate.cartmate.boundedContext.productService.shared.post.dto.DeleteProductRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PostDeletedEvent {
    private final DeleteProductRequest request;
}
