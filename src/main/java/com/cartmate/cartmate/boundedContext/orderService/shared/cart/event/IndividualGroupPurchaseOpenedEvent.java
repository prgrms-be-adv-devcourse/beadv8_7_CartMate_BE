package com.cartmate.cartmate.boundedContext.orderService.shared.cart.event;

import com.cartmate.cartmate.global.event.DomainEvent;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 개인물품 공동구매가 열렸다(회원 개설 · 어드민 수요 개설). cart가 아웃박스로 발행한다.
 * 받는 곳: order 쪽 공동구매 사본(ARCH-16). 사본은 {@code revision}이 더 클 때만 덮어쓴다.
 *
 * @param productIds 파는 상품 ID 전체
 * @param revision   공동구매 변경 번호 (열릴 때 0)
 */
public record IndividualGroupPurchaseOpenedEvent(
        Long individualGroupPurchaseId,
        LocalDateTime deadlineAt,
        List<Long> productIds,
        long revision,
        LocalDateTime openedAt
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
