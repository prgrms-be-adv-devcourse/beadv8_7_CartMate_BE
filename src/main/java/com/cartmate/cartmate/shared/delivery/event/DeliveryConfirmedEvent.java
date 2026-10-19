package com.cartmate.cartmate.shared.delivery.event;

import java.time.LocalDateTime;
import java.util.List;

// 배송이 구매확정될 때 발행. payout이 정산을 만들 때 사용
public record DeliveryConfirmedEvent(
        Long deliveryId,
        Long sellerId,
        Long orderId,
        LocalDateTime confirmedAt,
        List<Item> items
) {
    public record Item(
            Long orderItemId,
            Long unitPrice, // 옵션 가격이 반영된 최종 단가
            Long quantity
    ) {
    }
}
