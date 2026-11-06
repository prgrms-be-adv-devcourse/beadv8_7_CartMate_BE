package com.cartmate.cartmate.shared.cart.event;

import com.cartmate.cartmate.global.event.DomainEvent;

import java.time.LocalDateTime;

/**
 * 개인물품 공동구매가 닫혔다(CLOSED). cart가 아웃박스로 발행한다.
 * 받는 곳: order 쪽 공동구매 사본(ARCH-16) · order 쪽 페이백 판정(MAT-16).
 *
 * <p>주의: 회원 공동구매(MEMBER)는 마감 시각이 지나도 DB 상태가 OPEN으로 남고 시간 비교로만 닫힌 것으로 본다.
 * 그래서 마감 시각으로 저절로 끝난 회원 공동구매는 이 이벤트가 나가지 않는다. 받는 쪽은 {@code deadlineAt}과 함께 판단한다.
 * 개설자가 마감 시각 뒤에 마감하면 {@code closedAt}이 {@code deadlineAt}보다 늦다.
 *
 * @param origin      MEMBER(회원이 만든 공동구매) · ADMIN_DEMAND(어드민이 수요로 연 공동구매)
 * @param closeReason CREATOR(개설자 마감) · ADMIN(다른 어드민 마감) · DEADLINE(마감 시각 지나 시스템 마감) · SALES_STOPPED(판매 중지로 상품이 모두 빠짐)
 * @param deadlineAt  판매 마감 시각
 * @param revision    공동구매 변경 번호. 닫힘은 끝 상태라 받는 쪽은 이보다 작은 번호의 열림 · 갱신 이벤트를 버린다
 * @param paybackMinBuyers 열림 이벤트와 같은 값. 종료가 열림보다 먼저 와도 사본이 페이백 조건을 갖게 함께 싣는다
 * @param paybackAmount    열림 이벤트와 같은 값
 */
public record IndividualGroupPurchaseClosedEvent(
        Long individualGroupPurchaseId,
        String origin,
        String closeReason,
        LocalDateTime deadlineAt,
        LocalDateTime closedAt,
        long revision,
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
