package com.cartmate.cartmate.shared.order.event;

import com.cartmate.cartmate.shared.order.SharedGroupOrderCancelReason;

import com.cartmate.cartmate.global.event.DomainEvent;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 공동 주문(공용물품)이 취소됐다. order가 아웃박스로 발행한다.
 * payment가 이미 낸 분담금을 모두 환불하고, 판매 중지면 delivery가 배송을 취소하며, 기한 만료면 어뷰징이 미결제자 신호를 받는다.
 *
 * @param unpaidMemberIds 기한까지 분담금을 내지 않은 참여자(기한 만료일 때)
 *
 * <p>결제자 · 결제 ID · 금액(분배)은 싣지 않는다. 결제 원본은 payment가 가진다 — order가 결제를 지휘해 charge 때 payment에
 * 결제 행이 생기고, payment는 이 이벤트의 주문 ID(+ 회원 ID)로 자기 결제 행을 찾아 환불한다. 같은 값을 여기에도 실으면
 * 원본이 둘이 되어 어긋날 수 있다(ARCH-8에서 검토 후 뺌).
 */
public record SharedGroupOrderCanceledEvent(
        Long sharedGroupOrderId,
        Long sharedGroupPurchaseId,
        SharedGroupOrderCancelReason cancelReason,
        LocalDateTime canceledAt,
        List<Long> unpaidMemberIds
) implements DomainEvent {

    @Override
    public String aggregateType() {
        return "SharedGroupOrder";
    }

    @Override
    public String aggregateId() {
        return String.valueOf(sharedGroupOrderId);
    }
}
