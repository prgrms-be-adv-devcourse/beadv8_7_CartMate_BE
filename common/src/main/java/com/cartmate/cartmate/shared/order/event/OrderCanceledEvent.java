package com.cartmate.cartmate.shared.order.event;

import com.cartmate.cartmate.shared.order.OrderType;

import com.cartmate.cartmate.shared.order.OrderCancelReason;

import com.cartmate.cartmate.global.event.DomainEvent;

import java.time.LocalDateTime;

/**
 * @param refundAmount 결제 완료 주문이면 payment가 환불할 금액. order가 "결제 금액 − 판매글 내림으로 인한 취소로 이미 환불한 금액"으로
 *                     계산한다. 판매글 내림 환불 이벤트와 처리 순서가 뒤바뀌어도 합이 맞게 하려는 것이다 (ADR 0008).
 *                     필드가 생기기 전 이벤트에는 없어서 null이면 payment가 남은 금액 전부를 환불한다.
 */
public record OrderCanceledEvent(
        Long orderId,
        Long memberId,
        OrderType orderType,
        OrderCancelReason cancelReason,
        boolean paid,
        LocalDateTime canceledAt,
        Long refundAmount
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
