package com.cartmate.cartmate.boundedContext.productService.post.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 수정 실패 복구: 스냅샷에 있던 재고(같은 ID)를 이 추가금액 · 재고 수량으로 되살린다
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RestoreInventory {
    private Long id;
    private Long additionalPrice;
    private Integer stockQuantity;
}
