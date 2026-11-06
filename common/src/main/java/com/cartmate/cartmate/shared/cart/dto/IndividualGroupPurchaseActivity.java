package com.cartmate.cartmate.shared.cart.dto;

import java.time.LocalDateTime;

/**
 * 회원이 연 개인물품 공동구매 한 건. order-service 내부 API {@code GET /internal/group-purchases/individual}의 응답 계약(ARCH-8).
 *
 * @param open 지금 주문을 받는가(닫히지 않았고 판매 마감 전)
 */
public record IndividualGroupPurchaseActivity(
        Long id,
        String title,
        boolean open,
        int productCount,
        LocalDateTime deadlineAt
) {
}
