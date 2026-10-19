package com.cartmate.cartmate.boundedContext.order.dto.groupshared;

import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrder;
import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrderParticipant;
import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrderParticipantStatus;
import com.cartmate.cartmate.boundedContext.order.domain.groupshared.SharedGroupOrderStatus;

import java.time.LocalDateTime;

public record SharedGroupOrderSummaryResponse(
        Long id,
        Long sharedGroupPurchaseId,
        SharedGroupOrderStatus status,
        boolean creator,
        long totalAmount,
        long myShareAmount,
        SharedGroupOrderParticipantStatus myPaymentStatus,
        int paidCount,
        int participantCount,
        LocalDateTime paymentDeadlineAt,
        LocalDateTime createdAt
) {

    public static SharedGroupOrderSummaryResponse of(SharedGroupOrder order, Long viewerId) {
        SharedGroupOrderParticipant me = order.getParticipant(viewerId);
        int paidCount = (int) order.getParticipants().stream()
                .filter(SharedGroupOrderParticipant::isPaid)
                .count();
        return new SharedGroupOrderSummaryResponse(
                order.getId(),
                order.getSharedGroupPurchaseId(),
                order.getStatus(),
                order.isCreator(viewerId),
                order.getTotalAmount(),
                me.getShareAmount(),
                me.getStatus(),
                paidCount,
                order.getParticipants().size(),
                order.getPaymentDeadlineAt(),
                order.getCreatedAt()
        );
    }
}
