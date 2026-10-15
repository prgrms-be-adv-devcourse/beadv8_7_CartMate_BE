package com.cartmate.cartmate.domain.cart.client;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

// TODO: product 도메인 구현 완료 후 실제 구현체로 교체
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

    // 위 임시 SKU 데이터를 상품별로 묶어서 옵션 목록을 만든다.
    // 상품은 옵션이 하나라도 판매 중이면 판매 중으로 본다. (정강이 보호대는 유일한 옵션이 판매 중지라 상품도 판매 중지)
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
