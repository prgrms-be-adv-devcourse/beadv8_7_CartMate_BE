package com.cartmate.cartmate.shared.delivery.event;

import com.cartmate.cartmate.global.event.DomainEvent;
import com.cartmate.cartmate.shared.delivery.PurchaseType;
import com.cartmate.cartmate.shared.delivery.SalesStopCancelResult;

/**
 * 판매글 내림으로 인한 취소 요청을 판정한 결과. 배송 서버가 발행하고 order가 받아 항목 취소를 확정하거나 요청을 거절로 닫는다.
 * failureCode는 REJECTED일 때만 있다.
 */
public record DeliverySalesStopCancelResultEvent(
        PurchaseType purchaseType,
        Long orderId,
        Long sellerId,
        SalesStopCancelResult result,
        String failureCode
) implements DomainEvent {

    @Override
    public String aggregateType() {
        return "DeliveryOrder";
    }

    @Override
    public String aggregateId() {
        return String.valueOf(orderId);
    }
}
