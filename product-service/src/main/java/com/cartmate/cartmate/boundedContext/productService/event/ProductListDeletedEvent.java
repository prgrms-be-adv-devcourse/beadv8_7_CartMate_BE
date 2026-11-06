package com.cartmate.cartmate.boundedContext.productService.event;

import com.cartmate.cartmate.boundedContext.productService.product.dto.DeleteProductListRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ProductListDeletedEvent {
    private DeleteProductListRequest request;
}
