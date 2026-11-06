package com.cartmate.cartmate.shared.order.event;

import com.cartmate.cartmate.shared.order.OrderType;

import com.cartmate.cartmate.shared.order.OrderCancelReason;

import com.cartmate.cartmate.global.event.DomainEvent;

import java.time.LocalDateTime;

/**
 * 개인 주문 · 개인물품 공동구매 주문이 취소됐다. order가 아웃박스로 발행한다.
 * delivery가 결제 뒤 취소면 배송을 취소하고, payment가 {@code paid}면 전액 환불한다.
 *
 * <p>결제자 · 결제 ID · 금액(분배)은 싣지 않는다. 결제 원본은 payment가 가진다 — order가 결제를 지휘해 charge 때 payment에
 * 결제 행이 생기고, payment는 이 이벤트의 주문 ID(+ 회원 ID)로 자기 결제 행을 찾아 환불한다. 같은 값을 여기에도 실으면
 * 원본이 둘이 되어 어긋날 수 있다(ARCH-8에서 검토 후 뺌).
 */
public record OrderCanceledEvent(
        Long orderId,
        Long memberId,
        OrderType orderType,
        OrderCancelReason cancelReason,
        boolean paid,
        LocalDateTime canceledAt
) implements DomainEvent {

    @Override
    public String aggregateType() {
        return "Order";
    }

    @Override
    public String aggregateId() {
        return String.valueOf(orderId);
    }
}
