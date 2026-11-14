package com.cartmate.cartmate.boundedContext.productService.post.app;

import com.cartmate.cartmate.boundedContext.productService.post.dto.SalesPostDetailInventories;
import com.cartmate.cartmate.boundedContext.productService.post.dto.SalesPostDetailInventories.ProductInventories;
import com.cartmate.cartmate.boundedContext.productService.post.dto.SelectInventory;
import com.cartmate.cartmate.boundedContext.productService.post.dto.SelectProductOption;
import com.cartmate.cartmate.boundedContext.productService.post.dto.SelectProductResponse;
import com.cartmate.cartmate.boundedContext.productService.post.dto.SelectSalesPostInventory;
import com.cartmate.cartmate.boundedContext.productService.post.dto.SelectSalesPostOptionGroup;
import com.cartmate.cartmate.boundedContext.productService.post.dto.SelectSalesPostOptionValue;
import com.cartmate.cartmate.boundedContext.productService.post.out.SalesPostApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 판매글 상세에 붙일 상품별 선택 목록 · 재고를 상품 서버(GET /api/products/{postId})에서 가져온다.
 * 판매글 복제본에는 재고가 없다(목록 트래픽을 가볍게 두려고). 상세 조회가 몰릴 때를 대비해 Redis에 2초 캐시한다
 * (수정 · 삭제 뒤 최대 2초 예전 값이 보일 수 있다).
 */
@Component
@RequiredArgsConstructor
public class SalesPostDetailInventoryLoader {

    public static final String CACHE_NAME = "salesPostDetailInventory";

    private final SalesPostApiClient salesPostApiClient;

    @Cacheable(cacheNames = CACHE_NAME, key = "#postId")
    public SalesPostDetailInventories load(Long postId) {
        return new SalesPostDetailInventories(salesPostApiClient.getProduct(postId).stream()
                .map(SalesPostDetailInventoryLoader::toProductInventories)
                .toList());
    }

    private static ProductInventories toProductInventories(SelectProductResponse product) {
        List<SelectInventory> inventories = product.getInventoryList() == null ? List.of() : product.getInventoryList();
        return new ProductInventories(product.getId(), optionGroups(inventories),
                inventories.stream().map(SalesPostDetailInventoryLoader::toInventory).toList());
    }

    // 재고에 쓰인 상품옵션을 옵션 종류별로 모은다. 옵션 종류는 재고 이름(예: "265 / 블랙")에 나온 순서, 상품옵션은 ID 순(중복 없음)
    private static List<SelectSalesPostOptionGroup> optionGroups(List<SelectInventory> inventories) {
        Map<Long, String> typeNames = new LinkedHashMap<>();
        Map<Long, Map<Long, String>> valuesByType = new LinkedHashMap<>();
        for (SelectInventory inventory : inventories) {
            for (SelectProductOption option : inventory.getOptionList()) {
                typeNames.putIfAbsent(option.getProductTypeId(), option.getProductTypeName());
                valuesByType.computeIfAbsent(option.getProductTypeId(), typeId -> new LinkedHashMap<>())
                        .putIfAbsent(option.getProductOptionId(), option.getProductOptionName());
            }
        }

        List<SelectSalesPostOptionGroup> groups = new ArrayList<>();
        typeNames.forEach((typeId, typeName) -> groups.add(new SelectSalesPostOptionGroup(typeId, typeName,
                valuesByType.get(typeId).entrySet().stream()
                        .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                        .map(value -> new SelectSalesPostOptionValue(value.getKey(), value.getValue()))
                        .toList())));
        return groups;
    }

    private static SelectSalesPostInventory toInventory(SelectInventory inventory) {
        int stock = inventory.getStockQuantity() == null ? 0 : inventory.getStockQuantity();
        int reserved = inventory.getReservedQuantity() == null ? 0 : inventory.getReservedQuantity();
        return new SelectSalesPostInventory(
                inventory.getId(),
                inventory.getSkuName(),
                inventory.getAppliedPrice(),
                inventory.getAdditionalPrice(),
                Math.max(stock - reserved, 0),
                inventory.getOptionList().stream().map(SelectProductOption::getProductOptionId).toList());
    }
}
