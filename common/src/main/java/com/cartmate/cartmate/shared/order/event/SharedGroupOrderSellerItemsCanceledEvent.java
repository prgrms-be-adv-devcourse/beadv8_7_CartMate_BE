package com.cartmate.cartmate.shared.order.event;

import com.cartmate.cartmate.global.event.DomainEvent;

import java.time.LocalDateTime;

/**
 * 확정된 공동 주문에서 한 판매자의 상품만 취소됐을 때 order가 발행한다.
 * 다른 판매자 배송이 이미 발송돼 공동 주문 전체를 취소할 수 없을 때만 쓴다.
 *
 * <p>같은 트랜잭션에서 받는다. delivery가 그 판매자 배송을 취소하고(발송됐으면 예외 → 롤백),
 * payment가 amount를 결제한 사람들에게 낸 금액 비율대로(분담금이 같으면 1/n) 나눠 환불한다.
 *
 * <p>결제자 · 결제 ID · 금액(분배)은 싣지 않는다. 결제 원본은 payment가 가진다 — order가 결제를 지휘해 charge 때 payment에
 * 결제 행이 생기고, payment는 이 이벤트의 주문 ID(+ 회원 ID)로 자기 결제 행을 찾아 환불한다. 같은 값을 여기에도 실으면
 * 원본이 둘이 되어 어긋날 수 있다(ARCH-8에서 검토 후 뺌).
 */
public record SharedGroupOrderSellerItemsCanceledEvent(
        Long sharedGroupOrderId,
        Long sellerId,
        long amount,
        LocalDateTime canceledAt
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
