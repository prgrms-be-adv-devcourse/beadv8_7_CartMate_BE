package com.cartmate.cartmate.boundedContext.productService.post.domain;

import com.cartmate.cartmate.boundedContext.productService.post.dto.SelectInventory;
import com.cartmate.cartmate.boundedContext.productService.post.dto.SelectProductImage;
import com.cartmate.cartmate.boundedContext.productService.post.dto.SelectProductResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

public record SalesPostSnapshot(List<ProductSnapshot> products) {

    public static SalesPostSnapshot from(List<SelectProductResponse> responses) {
        return new SalesPostSnapshot(responses.stream().map(ProductSnapshot::from).toList());
    }

    // 재고 도입 전에 저장된 스냅샷(options 필드)도 읽을 수 있게 모르는 필드는 무시한다. 그런 스냅샷은 inventories가 비어 있다
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProductSnapshot(
            Long id,
            Long categoryId,
            String name,
            String description,
            Long basePrice,
            List<ImageSnapshot> images,
            List<InventorySnapshot> inventories,
            SalesPostProductStatus status   // 수정 전 판매 상태. 수정 끝 · 복구 때 이 상태로 되돌린다(상태를 담기 전 스냅샷은 null)
    ) {
        static ProductSnapshot from(SelectProductResponse r) {
            return new ProductSnapshot(
                    r.getId(),
                    r.getCategoryId(),
                    r.getName(),
                    r.getDescription(),
                    r.getBasePrice(),
                    r.getProductImageList().stream().map(ImageSnapshot::from).toList(),
                    r.getInventoryList() == null ? List.of()
                            : r.getInventoryList().stream().map(InventorySnapshot::from).toList(),
                    r.getStatus()
            );
        }
    }

    public record ImageSnapshot(Long imageId, String s3Key, int sortOrder) {
        static ImageSnapshot from(SelectProductImage i) {
            return new ImageSnapshot(i.getId(), i.getS3key(), i.getImageSeq());
        }
    }

    // 재고의 조합(선택된 옵션)은 바뀌지 않으므로 ID와 바뀔 수 있는 값(추가금액 · 재고 수량)만 남긴다
    public record InventorySnapshot(Long inventoryId, Long additionalPrice, Integer stockQuantity) {
        static InventorySnapshot from(SelectInventory i) {
            return new InventorySnapshot(i.getId(), i.getAdditionalPrice(), i.getStockQuantity());
        }
    }
}
