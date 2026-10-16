package com.cartmate.cartmate.boundedContext.cart.out;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Component
public class MockProductClient implements ProductClient {

    private static final Map<Long, CartProductInfo> PRODUCTS = Map.of(
            1L, new CartProductInfo(1L, 1L, "축구 유니폼", "M / 레드", 39000, "https://example.com/uniform.png", true),
            2L, new CartProductInfo(2L, 1L, "축구 유니폼", "L / 레드", 39000, "https://example.com/uniform.png", true),
            3L, new CartProductInfo(3L, 1L, "축구 유니폼", "L / 블루", 41000, "https://example.com/uniform.png", true),
            4L, new CartProductInfo(4L, 2L, "축구공", "5호", 25000, "https://example.com/ball.png", true),
            5L, new CartProductInfo(5L, 3L, "정강이 보호대", "FREE", 15000, "https://example.com/guard.png", false)
    );

    @Override
    public Optional<CartProductInfo> findByInventoryId(Long inventoryId) {
        return Optional.ofNullable(PRODUCTS.get(inventoryId));
    }

    @Override
    public List<CartProductInfo> findAllByInventoryIds(Collection<Long> inventoryIds) {
        return inventoryIds.stream()
                .map(PRODUCTS::get)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public List<CartProductOptions> findAllProductOptions(Collection<Long> productIds) {
        return productIds.stream()
                .distinct()
                .map(productId -> PRODUCTS.values().stream()
                        .filter(info -> info.productId().equals(productId))
                        .sorted(Comparator.comparing(CartProductInfo::inventoryId))
                        .toList())
                .filter(infos -> !infos.isEmpty())
                .map(infos -> new CartProductOptions(
                        infos.get(0).productId(),
                        infos.get(0).productName(),
                        infos.get(0).imageUrl(),
                        infos.stream().anyMatch(CartProductInfo::purchasable),
                        infos.stream()
                                .map(info -> new CartProductOptions.Option(
                                        info.inventoryId(), info.optionName(), info.price(), info.purchasable()))
                                .toList()))
                .toList();
    }
}
