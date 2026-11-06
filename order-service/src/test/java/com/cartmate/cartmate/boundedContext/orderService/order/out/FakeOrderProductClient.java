package com.cartmate.cartmate.boundedContext.orderService.order.out;

import com.cartmate.cartmate.support.TestCatalog;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Primary
@Profile("test")
public class FakeOrderProductClient implements OrderProductClient {

    private static final Map<Long, OrderProductInfo> PRODUCTS = TestCatalog.PRODUCTS.values().stream()
            .collect(Collectors.toUnmodifiableMap(TestCatalog.Product::inventoryId, product -> new OrderProductInfo(
                    product.inventoryId(), product.productId(), product.sellerId(), product.productName(),
                    product.optionName(), product.price(), product.imageUrl(), product.purchasable())));

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
