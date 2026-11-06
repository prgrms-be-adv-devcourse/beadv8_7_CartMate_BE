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
