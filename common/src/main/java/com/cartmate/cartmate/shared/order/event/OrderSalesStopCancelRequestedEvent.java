package com.cartmate.cartmate.shared.order.event;

import com.cartmate.cartmate.global.event.DomainEvent;
import com.cartmate.cartmate.shared.delivery.PurchaseType;

import java.time.LocalDateTime;

/**
 * 판매글 내림으로 인한 취소를 배송 서버에 요청할 때 order가 발행한다.
 * 배송 서버가 판정해 {@code DeliverySalesStopCancelResultEvent}로 답하고, order는 그 결과를 받은 뒤에만
 * 항목 취소를 확정하고 환불 이벤트를 낸다 (ADR 0008).
 *
 * <p>응답이 없으면 같은 내용을 새 이벤트 ID로 다시 보낸다. 받는 쪽은 같은 요청을 여러 번 받아도 같은 결과를 돌려준다.
 */
public record OrderSalesStopCancelRequestedEvent(
        PurchaseType purchaseType,
        Long orderId,
        Long sellerId,
        LocalDateTime requestedAt
) implements DomainEvent {

    @Override
    public String aggregateType() {
        return purchaseType == PurchaseType.SHARED_GROUP ? "SharedGroupOrder" : "Order";
    }

    @Override
    public String aggregateId() {
        return String.valueOf(orderId);
    }
}
