package com.cartmate.cartmate.shared.order.event;

import java.time.LocalDateTime;

/**
 * 결제 완료된 개인 주문에서 한 판매자의 상품만 취소됐을 때 order가 발행한다. (판매글 판매 중지 등)
 *
 * <p>같은 트랜잭션에서 받는다 ({@code @EventListener}).
 * delivery가 그 판매자 배송을 취소하고(발송됐으면 예외 → 전부 롤백), payment가 amount만큼 환불한다.
 * 구매자 주문 취소용 {@link OrderCanceledEvent}와 구분하기 위해 따로 둔다 (그 이벤트는 전액 환불을 일으킴).
 *
 * <p>환불 중복 방지 키는 payment가 주문·판매자로 정한다 ({@code RefundKey.orderSellerItems}).
 */
public record OrderSellerItemsCanceledEvent(
        Long orderId,
        Long buyerMemberId,
        Long sellerId,
        long amount,
        LocalDateTime canceledAt
) {
}