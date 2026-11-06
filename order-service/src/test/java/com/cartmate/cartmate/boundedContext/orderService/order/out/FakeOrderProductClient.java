package com.cartmate.cartmate.boundedContext.orderService.order.out;

import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

@Component
@Primary
@Profile("test")
public class FakeOrderProductClient implements OrderProductClient {

    private static final Map<Long, OrderProductInfo> PRODUCTS = Map.of(
            1L, new OrderProductInfo(1L, 1L, 1L, "축구 유니폼", "M / 레드", 39000, "https://example.com/uniform.png", true),
            2L, new OrderProductInfo(2L, 1L, 1L, "축구 유니폼", "L / 레드", 39000, "https://example.com/uniform.png", true),
            3L, new OrderProductInfo(3L, 1L, 1L, "축구 유니폼", "L / 블루", 41000, "https://example.com/uniform.png", true),
            4L, new OrderProductInfo(4L, 2L, 1L, "축구공", "5호", 25000, "https://example.com/ball.png", true),
            5L, new OrderProductInfo(5L, 3L, 1L, "정강이 보호대", "FREE", 15000, "https://example.com/guard.png", false)
    );

    // 테스트 하나가 상품 조회 결과를 잠시 바꿀 때 쓴다(예: 상품 없음 · DB 순단). @MockitoSpyBean을 쓰면 그 테스트만
    // 컨텍스트가 따로 떠서(ARCH-18) 공용 대역에 덮어쓰기를 둔다. 컨텍스트를 같이 쓰므로 바꾼 테스트가 끝날 때 clearOverride()로 되돌린다
    private volatile Function<Collection<Long>, List<OrderProductInfo>> override;

    public void overrideFindAll(Function<Collection<Long>, List<OrderProductInfo>> override) {
        this.override = override;
    }

    public void clearOverride() {
        this.override = null;
    }

    @Override
    public List<OrderProductInfo> findAllByInventoryIds(Collection<Long> inventoryIds) {
        Function<Collection<Long>, List<OrderProductInfo>> current = override;
        if (current != null) {
            return current.apply(inventoryIds);
        }
        return inventoryIds.stream()
                .distinct()
                .map(PRODUCTS::get)
                .filter(Objects::nonNull)
                .toList();
    }
}
