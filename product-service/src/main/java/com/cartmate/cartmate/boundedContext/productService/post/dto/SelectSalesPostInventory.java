package com.cartmate.cartmate.boundedContext.productService.post.dto;

import java.util.List;

/**
 * 상세 화면의 재고 하나: 사용자가 고른 상품옵션 조합(optionIds)으로 찾아 가격 · 수량을 보여 주고 장바구니에 담는다.
 *
 * @param stockQuantity 구매 가능한 수량 = 실제 재고 - 예약 재고 (0이면 품절)
 * @param optionIds     이 재고를 이루는 상품옵션 ID
 */
public record SelectSalesPostInventory(
        Long inventoryId,
        String skuName,
        Long appliedPrice,
        Long additionalPrice,
        Integer stockQuantity,
        List<Long> optionIds
) {
}
