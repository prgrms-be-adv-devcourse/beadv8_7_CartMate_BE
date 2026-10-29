package com.cartmate.cartmate.shared.order.event;

import java.time.LocalDateTime;

/**
 * 확정된 공동 주문에서 한 판매자의 상품만 취소됐을 때 order가 발행한다.
 * 다른 판매자 배송이 이미 발송돼 공동 주문 전체를 취소할 수 없을 때만 쓴다.
 *
 * <p>같은 트랜잭션에서 받는다. delivery가 그 판매자 배송을 취소하고(발송됐으면 예외 → 롤백),
 * payment가 amount를 결제한 사람들에게 낸 금액 비율대로(분담금이 같으면 1/n) 나눠 환불한다.
 */
public record SharedGroupOrderSellerItemsCanceledEvent(
        Long sharedGroupOrderId,
        Long sellerId,
        long amount,
        String cancelKey,
        LocalDateTime canceledAt
) {
}