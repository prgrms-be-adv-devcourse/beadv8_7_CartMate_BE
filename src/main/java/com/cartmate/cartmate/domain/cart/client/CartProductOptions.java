package com.cartmate.cartmate.domain.cart.client;

import java.util.List;

/*
 * 상품 하나와 그 옵션(SKU) 목록
 * 공동구매-개인물품 화면에서 "유니폼 → M/레드, L/레드, L/블루 중 선택"처럼 참여자가 옵션을 고를 수 있게 보여 줄 때 쓴다.
 *
 * CartProductInfo(SKU 하나 단위)와 따로 둔 이유
 * - 장바구니·공동물품은 이미 고른 SKU를 기준으로 조회해서 SKU 하나 단위 정보면 충분했다.
 * - 개인물품은 생성자가 "상품"만 정하고 옵션은 참여자가 고르므로, 상품을 기준으로 옵션 전체를 받아야 한다.
 *
 * 상품 도메인의 엔티티(Product, Inventory)를 그대로 쓰지 않는 이유: cart가 상품 도메인 내부 구조에 묶이지 않게,
 * cart 화면에 필요한 값만 담은 record를 cart 쪽에서 정한다. (ProductClient와 같은 이유)
 */
public record CartProductOptions(
        Long productId,
        String productName,
        String imageUrl,
        // 상품 자체가 판매 중인가 (판매 중지·숨김이면 false)
        boolean purchasable,
        List<Option> options
) {

    // 옵션(SKU) 하나. 이 안에서만 쓰여서 중첩 record로 둔다.
    public record Option(
            Long inventoryId,
            // 예: "M / 레드"
            String optionName,
            // 옵션별 추가 금액이 반영된 판매가
            int price,
            // 이 옵션을 지금 살 수 있는가. 상품은 판매 중이어도 특정 옵션만 판매 중지일 수 있다.
            // 재고 수량은 담지 않는다. 재고는 결제할 때 선점하기로 했다.
            boolean purchasable
    ) {
    }
}
