package com.cartmate.cartmate.boundedContext.orderService.cart.out;

import com.cartmate.cartmate.support.TestCatalog;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@Primary
@Profile("test")
public class FakeProductClient implements ProductClient {

    private static final Map<Long, CartProductInfo> PRODUCTS = TestCatalog.PRODUCTS.values().stream()
            .collect(Collectors.toUnmodifiableMap(TestCatalog.Product::inventoryId, product -> new CartProductInfo(
                    product.inventoryId(), product.productId(), product.productName(), product.optionName(),
                    product.price(), product.imageUrl(), product.purchasable())));

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
