package com.cartmate.cartmate.shared.product.event;

import com.cartmate.cartmate.shared.product.dto.DeleteProductListRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ProductListDeletedEvent {
    private DeleteProductListRequest request;
}
