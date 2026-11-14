package com.cartmate.cartmate.boundedContext.orderService.order.out;

import com.cartmate.cartmate.global.http.InternalApiClient;
import com.cartmate.cartmate.global.http.InternalApiClient.InternalResponse;
import com.cartmate.cartmate.shared.product.dto.InventoryForPurchase;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class HttpOrderProductClient implements OrderProductClient {

    // 재고 ID로 조회: 주문 항목 단위(상품 + 선택된 옵션 조합)의 옵션명 · 적용가 · 구매 가능 여부
    static final String PATH = "/internal/products/inventories/for-purchase?ids={ids}";
    static final ParameterizedTypeReference<InternalResponse<List<InventoryForPurchase>>> INVENTORIES =
            new ParameterizedTypeReference<>() {};

    private final InternalApiClient productServerApiClient;

    @Override
    public List<OrderProductInfo> findAllByInventoryIds(Collection<Long> inventoryIds) {
        String joined = inventoryIds == null ? "" : inventoryIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        if (joined.isEmpty()) {
            return List.of();
        }
        List<InventoryForPurchase> inventories = productServerApiClient.get(PATH, INVENTORIES, joined);
        return inventories == null ? List.of() : inventories.stream()
                .map(this::toOrderProductInfo)
                .toList();
    }

    private OrderProductInfo toOrderProductInfo(InventoryForPurchase inventory) {
        return new OrderProductInfo(
                inventory.inventoryId(),
                inventory.productId(),
                inventory.sellerId(),
                inventory.productName(),
                inventory.optionName(),
                inventory.price(),
                inventory.imageUrl(),
                inventory.purchasable()
        );
    }
}
