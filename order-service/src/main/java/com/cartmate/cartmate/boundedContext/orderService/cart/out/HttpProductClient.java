package com.cartmate.cartmate.boundedContext.orderService.cart.out;

import com.cartmate.cartmate.global.http.InternalApiClient;
import com.cartmate.cartmate.global.http.InternalApiClient.InternalResponse;
import com.cartmate.cartmate.shared.product.dto.InventoryForPurchase;
import com.cartmate.cartmate.shared.product.dto.ProductForPurchase;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class HttpProductClient implements ProductClient {

    // 상품 단위 정보(매칭 벡터용 이름 · 카테고리)
    static final String PATH = "/internal/products/for-purchase?ids={ids}";
    // 재고 ID로 조회: 장바구니에 담는 단위(상품 + 선택된 옵션 조합)
    static final String INVENTORY_PATH = "/internal/products/inventories/for-purchase?ids={ids}";
    // 상품 ID로 그 상품의 살아 있는 재고 목록 조회(공동구매 · 매칭의 옵션 선택)
    static final String INVENTORY_BY_PRODUCT_PATH =
            "/internal/products/inventories/for-purchase/by-products?productIds={productIds}";

    static final ParameterizedTypeReference<InternalResponse<List<ProductForPurchase>>> PRODUCTS =
            new ParameterizedTypeReference<>() {};
    static final ParameterizedTypeReference<InternalResponse<List<InventoryForPurchase>>> INVENTORIES =
            new ParameterizedTypeReference<>() {};

    private final InternalApiClient productServerApiClient;

    @Override
    public Optional<CartProductInfo> findByInventoryId(Long inventoryId) {
        return findAllByInventoryIds(List.of(inventoryId)).stream().findFirst();
    }

    @Override
    public List<CartProductInfo> findAllByInventoryIds(Collection<Long> inventoryIds) {
        return fetch(INVENTORY_PATH, INVENTORIES, inventoryIds).stream()
                .map(this::toCartProductInfo)
                .toList();
    }

    // 상품별로 재고를 묶는다(요청한 상품 순서). 살아 있는 재고가 없는 상품은 빠진다
    // 상품 단위 구매 가능 = 재고 중 하나라도 구매 가능
    @Override
    public List<CartProductOptions> findAllProductOptions(Collection<Long> productIds) {
        Map<Long, List<InventoryForPurchase>> inventoriesByProductId = fetch(INVENTORY_BY_PRODUCT_PATH, INVENTORIES, productIds)
                .stream()
                .collect(Collectors.groupingBy(InventoryForPurchase::productId, LinkedHashMap::new, Collectors.toList()));
        return productIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(inventoriesByProductId::get)
                .filter(Objects::nonNull)
                .map(this::toCartProductOptions)
                .toList();
    }

    @Override
    public List<CartProductProfile> findAllProductProfiles(Collection<Long> productIds) {
        return fetch(PATH, PRODUCTS, productIds).stream()
                .map(product -> new CartProductProfile(product.productId(), product.name(), product.categoryId()))
                .toList();
    }

    private <T> List<T> fetch(String path, ParameterizedTypeReference<InternalResponse<List<T>>> type,
                              Collection<Long> ids) {
        String joined = ids == null ? "" : ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        if (joined.isEmpty()) {
            return List.of();
        }
        List<T> result = productServerApiClient.get(path, type, joined);
        return result == null ? List.of() : result;
    }

    private CartProductInfo toCartProductInfo(InventoryForPurchase inventory) {
        return new CartProductInfo(
                inventory.inventoryId(),
                inventory.productId(),
                inventory.productName(),
                inventory.optionName(),
                Math.toIntExact(inventory.price()),
                inventory.imageUrl(),
                inventory.purchasable()
        );
    }

    private CartProductOptions toCartProductOptions(List<InventoryForPurchase> inventories) {
        InventoryForPurchase first = inventories.getFirst();
        return new CartProductOptions(
                first.productId(),
                first.productName(),
                first.imageUrl(),
                inventories.stream().anyMatch(InventoryForPurchase::purchasable),
                inventories.stream()
                        .map(inventory -> new CartProductOptions.Option(
                                inventory.inventoryId(),
                                inventory.optionName(),
                                Math.toIntExact(inventory.price()),
                                inventory.purchasable()))
                        .toList()
        );
    }
}
