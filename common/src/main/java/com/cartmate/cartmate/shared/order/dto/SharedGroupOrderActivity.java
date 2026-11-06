package com.cartmate.cartmate.shared.order.dto;

import com.cartmate.cartmate.shared.order.SharedGroupOrderParticipantStatus;
import com.cartmate.cartmate.shared.order.SharedGroupOrderStatus;

import java.time.LocalDateTime;

public record SharedGroupOrderActivity(
        Long id,
        Long sharedGroupPurchaseId,
        SharedGroupOrderStatus status,
        long myShareAmount,
        SharedGroupOrderParticipantStatus myPaymentStatus,
        int paidCount,
        int participantCount,
        LocalDateTime paymentDeadlineAt
) {
}
