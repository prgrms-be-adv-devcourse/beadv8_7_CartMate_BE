package com.cartmate.cartmate.shared.delivery.event;

import java.time.LocalDateTime;
import java.util.List;

// 배송이 구매확정될 때 발행. payout이 정산을 만들 때 사용
public record DeliveryConfirmedEvent(
        Long deliveryId,
        Long sellerId,
        Long orderId,
        String purchaseType, // INDIVIDUAL_GROUP, SHARED_GROUP. 개인 주문과 공동물품 주문은 주문상품 ID가 겹칠 수 있어 구분한다.
        LocalDateTime orderedAt, // 주문 시각. 배송이 만들어진 시각을 쓴다.
        LocalDateTime confirmedAt,
        List<Item> items
) {
    public record Item(
            Long orderItemId,
            String productName, // 주문 시점의 상품명 스냅샷
            Long unitPrice, // 옵션 가격이 반영된 최종 단가
            Long quantity
    ) {
    }
}
