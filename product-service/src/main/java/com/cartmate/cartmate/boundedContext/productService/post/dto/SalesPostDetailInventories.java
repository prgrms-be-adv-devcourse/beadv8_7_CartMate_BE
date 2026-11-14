package com.cartmate.cartmate.boundedContext.productService.post.dto;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 판매글 상세에 붙일 상품별 선택 목록 · 재고. 상품 서버에서 가져와 Redis에 잠깐(2초) 캐시한다.
 */
public record SalesPostDetailInventories(List<ProductInventories> products) {

    public record ProductInventories(
            Long productId,
            List<SelectSalesPostOptionGroup> optionGroups,
            List<SelectSalesPostInventory> inventories
    ) {
    }

    public Map<Long, ProductInventories> byProductId() {
        return products.stream().collect(Collectors.toMap(ProductInventories::productId, Function.identity()));
    }
}
