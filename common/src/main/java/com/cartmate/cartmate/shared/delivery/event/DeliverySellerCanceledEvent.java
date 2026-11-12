package com.cartmate.cartmate.shared.delivery.event;

import com.cartmate.cartmate.global.event.DomainEvent;
import com.cartmate.cartmate.shared.delivery.DeliveryCancelReason;
import com.cartmate.cartmate.shared.delivery.PurchaseType;

import java.time.LocalDateTime;

/**
 * 판매자가 자기 배송을 직접 취소했다(판매자 사유 취소). 배송 서버가 배송을 취소한 트랜잭션에서 발행하고,
 * order가 받아 그 판매자 항목을 취소하고 환불 이벤트를 낸다. 배송이 이미 취소됐으므로 order는 거절하지 않는다.
 */
public record DeliverySellerCanceledEvent(
        PurchaseType purchaseType,
        Long orderId,
        Long sellerId,
        DeliveryCancelReason cancelReason,
        LocalDateTime canceledAt
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
