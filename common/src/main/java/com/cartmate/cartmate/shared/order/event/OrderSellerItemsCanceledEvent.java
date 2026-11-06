package com.cartmate.cartmate.shared.order.event;

import com.cartmate.cartmate.global.event.DomainEvent;

import java.time.LocalDateTime;

/**
 * 결제 완료된 개인 주문에서 한 판매자의 상품만 취소됐을 때 order가 발행한다. (판매글 판매 중지 등)
 *
 * <p>아웃박스로 발행되고 delivery · payment(다른 서비스)는 Kafka로 받는다. delivery가 그 판매자 배송을 취소하고,
 * payment가 amount만큼 환불한다. 서비스가 나뉘어 예전처럼 "발송됐으면 delivery 예외로 취소 전체 롤백"은 되지 않는다(ARCH-9).
 * 구매자 주문 취소용 {@link OrderCanceledEvent}와 구분하기 위해 따로 둔다 (그 이벤트는 전액 환불을 일으킴).
 *
 * <p>환불 중복 방지 키는 payment가 주문·판매자로 정한다 ({@code RefundKey.orderSellerItems}).
 *
 * <p>결제자 · 결제 ID · 금액(분배)은 싣지 않는다. 결제 원본은 payment가 가진다 — order가 결제를 지휘해 charge 때 payment에
 * 결제 행이 생기고, payment는 이 이벤트의 주문 ID(+ 회원 ID)로 자기 결제 행을 찾아 환불한다. 같은 값을 여기에도 실으면
 * 원본이 둘이 되어 어긋날 수 있다(ARCH-8에서 검토 후 뺌).
 */
public record OrderSellerItemsCanceledEvent(
        Long orderId,
        Long buyerMemberId,
        Long sellerId,
        long amount,
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
