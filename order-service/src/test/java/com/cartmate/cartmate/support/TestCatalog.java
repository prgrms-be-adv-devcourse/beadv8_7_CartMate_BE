package com.cartmate.cartmate.support;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class TestCatalog {

    public record Product(
            Long inventoryId,
            Long productId,
            Long sellerId,
            String productName,
            String optionName,
            int price,
            String imageUrl,
            boolean purchasable
    ) {
    }

    public static final Map<Long, Product> PRODUCTS = List.of(
                    new Product(1L, 1L, 1L, "축구 유니폼", "M / 레드", 39000, "https://example.com/uniform.png", true),
                    new Product(2L, 1L, 1L, "축구 유니폼", "L / 레드", 39000, "https://example.com/uniform.png", true),
                    new Product(3L, 1L, 1L, "축구 유니폼", "L / 블루", 41000, "https://example.com/uniform.png", true),
                    new Product(4L, 2L, 1L, "축구공", "5호", 25000, "https://example.com/ball.png", true),
                    new Product(5L, 3L, 1L, "정강이 보호대", "FREE", 15000, "https://example.com/guard.png", false))
            .stream()
            .collect(Collectors.toUnmodifiableMap(Product::inventoryId, Function.identity()));

    private TestCatalog() {
    }

    public static String nickname(Long memberId) {
        return "회원" + memberId;
    }
}
