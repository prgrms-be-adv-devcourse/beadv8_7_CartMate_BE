package com.cartmate.cartmate.boundedContext.productService.product.domain;

import com.cartmate.cartmate.global.jpa.entity.BaseIdAndTime;
import com.cartmate.cartmate.shared.order.OrderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 재고 선점 기록: 주문 하나가 재고 하나를 몇 개 잡았는지.
 * 주문 취소 이벤트에는 주문 번호만 있어서, 선점 해제 · 재고 복구 때 이 기록으로 "어떤 재고를 몇 개"인지 찾는다.
 * 재고 · 주문은 다른 서버라 ID만 저장한다(FK 없음).
 * 주문 · 재고마다 한 줄만 둔다(유니크 키). 결제 실패 후 다시 결제하면 선점 해제된 그 줄을 다시 선점으로 쓴다.
 * 같은 주문이 동시에 두 번 선점되면 뒤에 온 쪽이 유니크 키(처음 선점) 또는 버전(다시 선점)에 막혀 롤백된다.
 */
@Entity
@Table(name = "PRODUCT_INVENTORY_RESERVATION",
        uniqueConstraints = @UniqueConstraint(name = "uk_inventory_reservation_order_inventory",
                columnNames = {"order_type", "order_id", "inventory_id"}))
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

    // 같은 기록을 동시에 고치면 뒤에 저장하는 쪽이 실패한다(다시 선점이 두 번 겹치는 경우)
    @Version
    private Long version;

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

    public boolean isConfirmed() {
        return status == InventoryReservationStatus.CONFIRMED;
    }

    public void release() {
        this.status = InventoryReservationStatus.RELEASED;
    }

    // 선점 해제된 기록을 다시 선점으로(결제 실패 후 다시 결제). 수량은 새 요청 값으로
    public void reserveAgain(int quantity) {
        this.quantity = quantity;
        this.status = InventoryReservationStatus.RESERVED;
    }

    public void confirm() {
        this.status = InventoryReservationStatus.CONFIRMED;
    }

    public void restore() {
        this.status = InventoryReservationStatus.RESTORED;
    }
}
