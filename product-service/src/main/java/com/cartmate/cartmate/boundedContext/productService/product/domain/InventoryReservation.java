package com.cartmate.cartmate.boundedContext.productService.product.domain;

import com.cartmate.cartmate.global.jpa.entity.BaseIdAndTime;
import com.cartmate.cartmate.shared.order.OrderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 재고 선점 기록: 주문 하나가 재고 하나를 몇 개 잡았는지.
 * 주문 취소 이벤트에는 주문 번호만 있어서, 선점 해제 · 재고 복구 때 이 기록으로 "어떤 재고를 몇 개"인지 찾는다.
 * 재고 · 주문은 다른 서버라 ID만 저장한다(FK 없음).
 */
@Entity
@Table(name = "PRODUCT_INVENTORY_RESERVATION",
        indexes = @Index(name = "idx_inventory_reservation_order", columnList = "order_type, order_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryReservation extends BaseIdAndTime {

    @Column(name = "order_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private OrderType orderType;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "inventory_id", nullable = false)
    private Long inventoryId;

    // 판매자 몫 취소(그 판매자 상품만 재고 복구)에서 쓴다
    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private InventoryReservationStatus status;

    public InventoryReservation(OrderType orderType, Long orderId, Long inventoryId, Long sellerId, int quantity) {
        this.orderType = orderType;
        this.orderId = orderId;
        this.inventoryId = inventoryId;
        this.sellerId = sellerId;
        this.quantity = quantity;
        this.status = InventoryReservationStatus.RESERVED;
    }

    public boolean isReserved() {
        return status == InventoryReservationStatus.RESERVED;
    }

    // 선점이 살아 있거나 이미 판매 확정된 기록 → 같은 주문을 다시 선점하지 않는다
    public boolean isHolding() {
        return status == InventoryReservationStatus.RESERVED || status == InventoryReservationStatus.CONFIRMED;
    }

    public void release() {
        this.status = InventoryReservationStatus.RELEASED;
    }
}
