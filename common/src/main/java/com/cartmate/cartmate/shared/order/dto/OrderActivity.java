package com.cartmate.cartmate.shared.order.dto;

import com.cartmate.cartmate.shared.order.OrderStatus;
import com.cartmate.cartmate.shared.order.OrderType;

import java.time.LocalDateTime;

/**
 * 회원의 개인 · 개인물품 공동구매 주문 한 건. order-service 내부 API {@code GET /internal/orders}의 응답 계약(ARCH-8).
 * 회원 서버가 마이페이지 활동에 쓴다. 생산자는 order-service이고 필드는 추가만 한다(이름 변경 · 삭제는 받는 쪽과 먼저 맞춤).
 */
public record OrderActivity(
        Long id,
        String orderNumber,
        OrderType orderType,
        OrderStatus status,
        long totalAmount,
        int itemCount,
        String firstProductName,
        LocalDateTime createdAt
) {
}
