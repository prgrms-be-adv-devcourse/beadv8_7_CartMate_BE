package com.cartmate.cartmate.boundedContext.orderService.cart.out;

import com.cartmate.cartmate.global.http.InternalApiClient;
import com.cartmate.cartmate.global.http.InternalApiClient.InternalResponse;
import com.cartmate.cartmate.shared.product.dto.ProductForPurchase;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class HttpProductClient implements ProductClient {

    static final String PATH = "/internal/products/for-purchase?ids={ids}";
    static final ParameterizedTypeReference<InternalResponse<List<ProductForPurchase>>> PRODUCTS =
            new ParameterizedTypeReference<>() {};

    private final InternalApiClient productServerApiClient;

    @Override
    public Optional<CartProductInfo> findByInventoryId(Long inventoryId) {
        return findAllByInventoryIds(List.of(inventoryId)).stream().findFirst();
    }

    @Override
    public List<CartProductInfo> findAllByInventoryIds(Collection<Long> inventoryIds) {
        return fetch(inventoryIds).stream()
                .map(this::toCartProductInfo)
                .toList();
    }

    @Override
    public List<CartProductOptions> findAllProductOptions(Collection<Long> productIds) {
        return fetch(productIds).stream()
                .map(this::toCartProductOptions)
                .toList();
    }

    @Override
    public List<CartProductProfile> findAllProductProfiles(Collection<Long> productIds) {
        return fetch(productIds).stream()
                .map(product -> new CartProductProfile(product.productId(), product.name(), product.categoryId()))
                .toList();
    }

    private List<ProductForPurchase> fetch(Collection<Long> ids) {
        String joined = ids == null ? "" : ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        if (joined.isEmpty()) {
            return List.of();
        }
        List<ProductForPurchase> products = productServerApiClient.get(PATH, PRODUCTS, joined);
        return products == null ? List.of() : products;
    }

    private CartProductInfo toCartProductInfo(ProductForPurchase product) {
        return new CartProductInfo(
                product.productId(),
                product.productId(),
                product.name(),
                null,
                Math.toIntExact(product.basePrice()),
                product.imageUrl(),
                product.purchasable()
        );
    }

    private CartProductOptions toCartProductOptions(ProductForPurchase product) {
        return new CartProductOptions(
                product.productId(),
                product.name(),
                product.imageUrl(),
                product.purchasable(),
                List.of(new CartProductOptions.Option(
                        product.productId(),
                        null,
                        Math.toIntExact(product.basePrice()),
                        product.purchasable()
                ))
        );
    }
}
