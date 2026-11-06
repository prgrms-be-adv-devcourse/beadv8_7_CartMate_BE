package com.cartmate.cartmate.boundedContext.orderService.order.out;

import com.cartmate.cartmate.global.http.InternalApiClient;
import com.cartmate.cartmate.global.http.InternalApiClient.InternalResponse;
import com.cartmate.cartmate.shared.product.dto.ProductForPurchase;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

// 상품 서버 내부 API(/internal/products/for-purchase)를 HTTP로 부른다. 통신 공통 처리는 InternalApiClient가 한다.
@Component
@RequiredArgsConstructor
class HttpOrderProductClient implements OrderProductClient {

    static final String PATH = "/internal/products/for-purchase?ids={ids}";
    static final ParameterizedTypeReference<InternalResponse<List<ProductForPurchase>>> PRODUCTS =
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
        List<ProductForPurchase> products = productServerApiClient.get(PATH, PRODUCTS, joined);
        return products == null ? List.of() : products.stream()
                .map(this::toOrderProductInfo)
                .toList();
    }

    // 상품 서버의 옵션 모델이 아직 없어 상품 하나 = 재고 하나로 본다 (inventoryId = productId)
    private OrderProductInfo toOrderProductInfo(ProductForPurchase product) {
        return new OrderProductInfo(
                product.productId(),
                product.productId(),
                product.sellerId(),
                product.name(),
                null,
                product.basePrice(),
                product.imageUrl(),
                product.purchasable()
        );
    }
}
