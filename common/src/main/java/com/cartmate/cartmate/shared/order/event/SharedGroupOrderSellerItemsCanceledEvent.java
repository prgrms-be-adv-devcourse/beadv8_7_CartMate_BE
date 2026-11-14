package com.cartmate.cartmate.shared.order.event;

import com.cartmate.cartmate.global.event.DomainEvent;
import com.cartmate.cartmate.shared.delivery.DeliveryCancelReason;

import java.time.LocalDateTime;

/**
 * 확정된 공동 주문에서 한 판매자의 상품만 취소됐을 때 order가 발행한다.
 * 판매글 내림으로 인한 취소에서 다른 판매자 배송이 이미 발송돼 공동 주문 전체를 취소할 수 없을 때, 또는 판매자 사유 취소일 때 쓴다.
 *
 * payment가 amount를 결제한 사람들에게 낸 금액 비율대로(분담금이 같으면 1/n) 나눠 환불한다.
 *
 * @param cancelReason 그 판매자 몫이 취소된 사유. 이 필드가 생기기 전 이벤트에는 없어서 null이면 판매 중지로 본다.
 */
public record SharedGroupOrderSellerItemsCanceledEvent(
        Long sharedGroupOrderId,
        Long sellerId,
        long amount,
        LocalDateTime canceledAt,
        DeliveryCancelReason cancelReason
) implements DomainEvent {

    public SharedGroupOrderSellerItemsCanceledEvent(Long sharedGroupOrderId, Long sellerId, long amount,
                                                    LocalDateTime canceledAt) {
        this(sharedGroupOrderId, sellerId, amount, canceledAt, DeliveryCancelReason.SALES_STOPPED);
    }

    @Override
    public String aggregateType() {
        return "SharedGroupOrder";
    }

    @Override
    public String aggregateId() {
        return String.valueOf(sharedGroupOrderId);
    }
}
