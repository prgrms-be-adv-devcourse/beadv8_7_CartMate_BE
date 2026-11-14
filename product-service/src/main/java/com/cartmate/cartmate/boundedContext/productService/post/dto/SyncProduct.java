package com.cartmate.cartmate.boundedContext.productService.post.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SyncProduct {
    private Long id;
    private Long sellerId;
    private Long salesPostId;
    private Long categoryId;
    private String name;
    private String description;
    private Long basePrice;
    private LocalDateTime createDate;
    private LocalDateTime modifyDate;
    private List<SyncProductImage> productImageList;
    private List<SyncProductOption> productOptionList;
    // 수정에서 빠진 재고 ID (수정 응답에만 채운다. 등록 · 복구 응답은 빈 목록). post가 수정 완료 때 InventoryRemovedEvent로 알린다
    private List<Long> removedInventoryIds = new ArrayList<>();
}
