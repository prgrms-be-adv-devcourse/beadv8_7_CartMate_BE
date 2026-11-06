package com.cartmate.cartmate.shared.order.dto;

import com.cartmate.cartmate.shared.order.SharedGroupOrderParticipantStatus;
import com.cartmate.cartmate.shared.order.SharedGroupOrderStatus;

import java.time.LocalDateTime;

/**
 * 회원이 참여한 공동 주문 한 건. order-service 내부 API {@code GET /internal/group-orders/shared}의 응답 계약(ARCH-8).
 * 공동구매 제목은 담지 않는다 — 회원 서버가 {@code /internal/group-purchases/shared}의 제목으로 맞춘다.
 *
 * @param myPaymentStatus 이 회원의 분담금 결제 상태
 */
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
