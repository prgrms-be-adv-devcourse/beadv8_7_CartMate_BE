package com.cartmate.cartmate.domain.cart.client;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductClient {

    Optional<CartProductInfo> findByInventoryId(Long inventoryId);

    List<CartProductInfo> findAllByInventoryIds(Collection<Long> inventoryIds);
}
