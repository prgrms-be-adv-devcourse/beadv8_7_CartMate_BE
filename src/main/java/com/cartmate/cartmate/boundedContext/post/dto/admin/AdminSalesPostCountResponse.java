package com.cartmate.cartmate.boundedContext.post.dto.admin;

// 관리자 판매글 화면 상단 집계
public record AdminSalesPostCountResponse(
        long total,
        long onSale,
        long pausedBySeller,
        long suspendedByAdmin
) {
}
