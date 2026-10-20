package com.cartmate.cartmate.boundedContext.order.out;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class MockOrderProductClient implements OrderProductClient {

    private static final Map<Long, OrderProductInfo> PRODUCTS = Map.of(
            1L, new OrderProductInfo(1L, 1L, 1L, "축구 유니폼", "M / 레드", 39000, "https://example.com/uniform.png", true),
            2L, new OrderProductInfo(2L, 1L, 1L, "축구 유니폼", "L / 레드", 39000, "https://example.com/uniform.png", true),
            3L, new OrderProductInfo(3L, 1L, 1L, "축구 유니폼", "L / 블루", 41000, "https://example.com/uniform.png", true),
            4L, new OrderProductInfo(4L, 2L, 1L, "축구공", "5호", 25000, "https://example.com/ball.png", true),
            5L, new OrderProductInfo(5L, 3L, 1L, "정강이 보호대", "FREE", 15000, "https://example.com/guard.png", false)
    );

    @Override
    public List<OrderProductInfo> findAllByInventoryIds(Collection<Long> inventoryIds) {
        return inventoryIds.stream()
                .distinct()
                .map(PRODUCTS::get)
                .filter(Objects::nonNull)
                .toList();
    }
}
