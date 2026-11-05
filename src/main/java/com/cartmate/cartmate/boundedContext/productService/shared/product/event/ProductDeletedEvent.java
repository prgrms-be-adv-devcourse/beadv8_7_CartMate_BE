package com.cartmate.cartmate.boundedContext.productService.shared.product.event;

import com.cartmate.cartmate.boundedContext.productService.shared.post.dto.DeleteProductRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ProductDeletedEvent {
    private final DeleteProductRequest request;
}
