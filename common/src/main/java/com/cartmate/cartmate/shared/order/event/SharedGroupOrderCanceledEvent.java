package com.cartmate.cartmate.shared.order.event;

import com.cartmate.cartmate.shared.order.SharedGroupOrderCancelReason;

import com.cartmate.cartmate.global.event.DomainEvent;

import java.time.LocalDateTime;
import java.util.List;

public record SharedGroupOrderCanceledEvent(
        Long sharedGroupOrderId,
        Long sharedGroupPurchaseId,
        SharedGroupOrderCancelReason cancelReason,
        LocalDateTime canceledAt,
        List<Long> unpaidMemberIds,
        Long creatorMemberId,
        List<SellerAmount> canceledSellerAmounts  // null이면 이 필드가 생기기 전 이벤트: payment가 남은 금액 전부를 환불한다
) implements DomainEvent {

    /**
     * 전체 취소보다 먼저 따로 취소된 판매자 몫(판매자와 그 몫의 금액).
     * 이 몫은 판매자 몫 환불로 따로 돌려주므로, payment는 전체 취소 환불에서 참여자마다 이 몫을 빼고 환불한다.
     */
    public record SellerAmount(Long sellerId, long amount) {
    }

    public SharedGroupOrderCanceledEvent(Long sharedGroupOrderId, Long sharedGroupPurchaseId,
                                         SharedGroupOrderCancelReason cancelReason, LocalDateTime canceledAt,
                                         List<Long> unpaidMemberIds, Long creatorMemberId) {
        this(sharedGroupOrderId, sharedGroupPurchaseId, cancelReason, canceledAt, unpaidMemberIds, creatorMemberId, null);
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
