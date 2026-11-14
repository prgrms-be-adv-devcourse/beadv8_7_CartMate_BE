package com.cartmate.cartmate.shared.product.dto;

// 장바구니 · 주문이 재고 하나(상품 + 선택된 옵션 조합)를 담을 때 쓰는 구매용 정보
// optionName: 재고의 skuName(예: "265 / 블랙", 옵션 없는 상품은 "기본"), price: 적용가(기본가 + 추가금액)
// purchasable: 상품 판매 중 + 재고 삭제 안 됨 + 남은 수량(재고 - 예약) 1개 이상
public record InventoryForPurchase(
        Long inventoryId,
        Long productId,
        Long sellerId,
        String productName,
        String optionName,
        Long price,
        String imageUrl,
        Long categoryId,
        boolean purchasable
) {
}
