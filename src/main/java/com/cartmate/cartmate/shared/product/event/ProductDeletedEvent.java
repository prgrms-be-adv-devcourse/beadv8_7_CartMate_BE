package com.cartmate.cartmate.shared.product.event;

import com.cartmate.cartmate.shared.product.dto.ProductRollbackRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ProductDeletedEvent {
    private final ProductRollbackRequest productRollbackRequest;
}
