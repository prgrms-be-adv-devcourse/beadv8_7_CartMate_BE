package com.cartmate.cartmate.boundedContext.orderService.shared.cart.event;

import com.cartmate.cartmate.global.event.DomainEvent;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 열려 있는 개인물품 공동구매의 판매 조건이 바뀌었다(상품 추가 · 제거 · 마감 시각 변경 · 판매 중지로 일부 상품 제외).
 * 바뀐 부분이 아니라 바뀐 뒤 전체 상태를 싣는다. 받는 쪽은 {@code revision}이 더 클 때만 덮어쓴다(릴레이는 순서를 보장하지 않음).
 * 닫힐 때는 이 이벤트가 아니라 {@link IndividualGroupPurchaseClosedEvent}가 나간다.
 *
 * @param productIds 바뀐 뒤 파는 상품 ID 전체
 */
public record IndividualGroupPurchaseUpdatedEvent(
        Long individualGroupPurchaseId,
        LocalDateTime deadlineAt,
        List<Long> productIds,
        long revision,
        LocalDateTime updatedAt
) implements DomainEvent {

    @Override
    public String aggregateType() {
        return "IndividualGroupPurchase";
    }

    @Override
    public String aggregateId() {
        return String.valueOf(individualGroupPurchaseId);
    }
}
