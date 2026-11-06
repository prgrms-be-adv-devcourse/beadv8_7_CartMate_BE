package com.cartmate.cartmate.support;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 테스트 대역(Fake 상품 · 회원 클라이언트)이 함께 쓰는 상품 · 회원 데이터.
 * cart와 order는 서로 다른 포트로 상품 · 회원을 조회하므로 대역 클래스는 모듈마다 두고, 데이터만 여기서 읽는다.
 */
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

    /** 재고 ID(inventoryId)로 찾는 상품 옵션 5개. 5번(정강이 보호대)만 구매할 수 없다. */
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
