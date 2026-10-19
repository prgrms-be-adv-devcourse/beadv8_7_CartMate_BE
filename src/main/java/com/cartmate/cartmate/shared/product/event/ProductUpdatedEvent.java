package com.cartmate.cartmate.shared.product.event;

import com.cartmate.cartmate.shared.product.dto.SyncProductRequest;

public record ProductUpdatedEvent(SyncProductRequest syncProductRequest) {
}
