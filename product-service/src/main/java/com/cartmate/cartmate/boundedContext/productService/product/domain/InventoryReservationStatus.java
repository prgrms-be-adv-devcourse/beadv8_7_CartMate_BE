package com.cartmate.cartmate.boundedContext.productService.product.domain;

// 재고 선점 기록 상태
// RESERVED → RELEASED (결제 실패 · 결제 기한 초과) / RESERVED → CONFIRMED (결제 성공) → RESTORED (결제 후 취소)
public enum InventoryReservationStatus {
    RESERVED("선점"),
    RELEASED("선점 해제"),
    CONFIRMED("판매 확정"),
    RESTORED("재고 복구");

    private final String label;

    InventoryReservationStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
