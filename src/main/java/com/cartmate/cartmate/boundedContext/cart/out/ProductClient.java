package com.cartmate.cartmate.boundedContext.cart.out;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface ProductClient {

    Optional<CartProductInfo> findByInventoryId(Long inventoryId);

    List<CartProductInfo> findAllByInventoryIds(Collection<Long> inventoryIds);

    List<CartProductOptions> findAllProductOptions(Collection<Long> productIds);


    default Set<Long> findUnavailableInventoryIds(Collection<Long> inventoryIds) {
        Map<Long, CartProductInfo> infos = findAllByInventoryIds(inventoryIds).stream()
                .collect(Collectors.toMap(CartProductInfo::inventoryId, Function.identity()));

        return inventoryIds.stream()
                .filter(inventoryId -> {
                    CartProductInfo info = infos.get(inventoryId);
                    return info == null || !info.purchasable();
                })
                .collect(Collectors.toSet());
    }
}
