package com.cartmate.cartmate.shared.cart.event;

import com.cartmate.cartmate.global.event.DomainEvent;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 개인물품 공동구매가 열렸다(회원 개설 · 어드민 수요 개설). cart가 아웃박스로 발행한다.
 * 받는 곳: order 쪽 공동구매 사본(ARCH-16). 사본은 {@code revision}이 더 클 때만 덮어쓴다.
 *
 * @param productIds 파는 상품 ID 전체
 * @param revision   공동구매 변경 번호 (열릴 때 0)
 * @param paybackMinBuyers 페이백 기준 인원. 어드민 수요 공동구매만 있고 회원 공동구매는 null. 공동구매 동안 바뀌지 않는다
 * @param paybackAmount    1인당 페이백 금액. paybackMinBuyers와 함께 있거나 함께 null
 */
public record IndividualGroupPurchaseOpenedEvent(
        Long individualGroupPurchaseId,
        LocalDateTime deadlineAt,
        List<Long> productIds,
        long revision,
        LocalDateTime openedAt,
        Integer paybackMinBuyers,
        Long paybackAmount
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
