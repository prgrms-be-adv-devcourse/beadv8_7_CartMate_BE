package com.cartmate.cartmate.shared.cart.dto;

import com.cartmate.cartmate.shared.cart.SharedGroupPurchaseStatus;

import java.time.LocalDateTime;

/**
 * 회원이 참여한 공용물품 공동구매 한 건. order-service 내부 API {@code GET /internal/group-purchases/shared}의 응답 계약(ARCH-8).
 * 회원 서버가 마이페이지 활동에 쓴다. 필드는 추가만 한다.
 */
public record SharedGroupPurchaseActivity(
        Long id,
        String title,
        SharedGroupPurchaseStatus status,
        int participantCount,
        int maxParticipants,
        LocalDateTime editDeadlineAt
) {
}
