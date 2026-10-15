package com.cartmate.cartmate.domain.cart.client;

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

    // 여러 상품의 옵션 목록을 한 번에 가져온다. (공동구매-개인물품 화면, 생성 시 상품 확인)
    // 상품마다 따로 조회하면 상품 수만큼 조회가 나가는 N+1이 생겨서 묶어서 조회한다.
    // 없는(삭제된) 상품은 결과에서 빠진다. 상품 하나당 결과도 하나여야 한다.
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
