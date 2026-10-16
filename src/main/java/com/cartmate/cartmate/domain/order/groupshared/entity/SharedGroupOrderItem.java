package com.cartmate.cartmate.domain.order.groupshared.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "shared_group_order_item")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SharedGroupOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shared_group_order_id", nullable = false)
    private SharedGroupOrder sharedGroupOrder;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Long inventoryId;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private long unitPrice;

    private LocalDateTime refundedAt;

    private SharedGroupOrderItem(SharedGroupOrder sharedGroupOrder, Long productId, Long inventoryId,
                                 int quantity, long unitPrice) {
        if (quantity < 1) {
            throw new IllegalArgumentException("수량은 1개 이상이어야 합니다.");
        }
        if (unitPrice < 0) {
            throw new IllegalArgumentException("가격은 0원 이상이어야 합니다.");
        }
        this.sharedGroupOrder = sharedGroupOrder;
        this.productId = productId;
        this.inventoryId = inventoryId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    static SharedGroupOrderItem create(SharedGroupOrder sharedGroupOrder, Long productId, Long inventoryId,
                                       int quantity, long unitPrice) {
        return new SharedGroupOrderItem(sharedGroupOrder, productId, inventoryId, quantity, unitPrice);
    }

    public long amount() {
        return unitPrice * quantity;
    }

    public boolean isRefunded() {
        return refundedAt != null;
    }

    void markRefunded(LocalDateTime now) {
        if (isRefunded()) {
            throw new IllegalArgumentException("이미 환불한 항목입니다.");
        }
        this.refundedAt = now;
    }
}
