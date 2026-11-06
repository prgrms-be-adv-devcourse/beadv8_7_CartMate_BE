package com.cartmate.cartmate.shared.product.event;

import com.cartmate.cartmate.shared.product.dto.SyncProductRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ProductCreatedEvent {
    private SyncProductRequest request;
}
