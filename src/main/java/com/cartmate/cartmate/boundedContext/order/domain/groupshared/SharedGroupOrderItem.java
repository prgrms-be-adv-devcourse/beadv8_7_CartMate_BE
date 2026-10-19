package com.cartmate.cartmate.boundedContext.order.domain.groupshared;

import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.boundedContext.order.exception.OrderErrorCode;
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
            throw new ServiceException(OrderErrorCode.ORDER_INVALID_QUANTITY);
        }
        if (unitPrice < 0) {
            throw new ServiceException(OrderErrorCode.ORDER_INVALID_PRICE);
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
            throw new ServiceException(OrderErrorCode.ORDER_ITEM_ALREADY_REFUNDED);
        }
        this.refundedAt = now;
    }
}
