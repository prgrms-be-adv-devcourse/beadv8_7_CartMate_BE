package com.cartmate.cartmate.shared.order.event;

import com.cartmate.cartmate.global.event.DomainEvent;
import com.cartmate.cartmate.shared.delivery.DeliveryCancelReason;

import java.time.LocalDateTime;

/**
 * 결제 완료된 개인 주문에서 한 판매자의 상품만 취소됐을 때 order가 발행한다. (판매글 내림으로 인한 취소 또는 판매자 사유 취소)
 *
 * 구매자 주문 취소용 {@link OrderCanceledEvent}와 구분하기 위해 따로 둔다 (그 이벤트는 전액 환불을 일으킴).
 *
 * <p>환불 중복 방지 키는 payment가 주문·판매자로 정한다 ({@code RefundKey.orderSellerItems}).
 * 그래서 같은 판매자 몫이 판매 중지와 판매자 사유로 두 번 와도 한 번만 환불된다.
 *
 * @param cancelReason 그 판매자 몫이 취소된 사유. 이 필드가 생기기 전 이벤트에는 없어서 null이면 판매 중지로 본다.
 */
public record OrderSellerItemsCanceledEvent(
        Long orderId,
        Long buyerMemberId,
        Long sellerId,
        long amount,
        LocalDateTime canceledAt,
        DeliveryCancelReason cancelReason
) implements DomainEvent {

    public OrderSellerItemsCanceledEvent(Long orderId, Long buyerMemberId, Long sellerId, long amount,
                                         LocalDateTime canceledAt) {
        this(orderId, buyerMemberId, sellerId, amount, canceledAt, DeliveryCancelReason.SALES_STOPPED);
    }

    @Override
    public String aggregateType() {
        return "Order";
    }

    @Override
    public String aggregateId() {
        return String.valueOf(orderId);
    }
}
