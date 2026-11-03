package com.cartmate.cartmate.shared.order.event;

import com.cartmate.cartmate.shared.event.DomainEvent;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문(개인 · 개인물품 공동구매)이 결제 대기로 만들어졌을 때 order가 아웃박스로 발행한다.
 * 지금 받는 곳은 cart의 개인물품 공동구매 주문 읽기 모델뿐이다(릴레이 래퍼로 받음).
 */
public record OrderCreatedEvent(
        Long orderId,
        String orderNumber,
        Long memberId,
        String orderType,
        Long individualGroupPurchaseId,
        LocalDateTime createdAt,
        List<Item> items
) implements DomainEvent {

    @Override
    public String aggregateType() {
        return "Order";
    }

    @Override
    public String aggregateId() {
        return String.valueOf(orderId);
    }

    public record Item(
            Long orderItemId,
            Long productId,
            Long inventoryId,
            Long sellerId,
            int quantity,
            long unitPrice
    ) {
    }
}
