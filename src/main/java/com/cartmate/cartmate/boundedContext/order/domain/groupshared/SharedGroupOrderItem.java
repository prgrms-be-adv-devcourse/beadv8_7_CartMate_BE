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

    private Long sellerId;

    @Column(nullable = false, length = 100)
    private String productName;

    @Column(length = 100)
    private String optionName;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private long unitPrice;

    private SharedGroupOrderItem(SharedGroupOrder sharedGroupOrder, SharedGroupOrder.OrderItemLine line) {
        if (line.quantity() < 1) {
            throw new ServiceException(OrderErrorCode.ORDER_INVALID_QUANTITY);
        }
        if (line.unitPrice() < 0) {
            throw new ServiceException(OrderErrorCode.ORDER_INVALID_PRICE);
        }
        this.sharedGroupOrder = sharedGroupOrder;
        this.productId = line.productId();
        this.inventoryId = line.inventoryId();
        this.sellerId = line.sellerId();
        this.productName = line.productName();
        this.optionName = line.optionName();
        this.quantity = line.quantity();
        this.unitPrice = line.unitPrice();
    }

    static SharedGroupOrderItem create(SharedGroupOrder sharedGroupOrder, SharedGroupOrder.OrderItemLine line) {
        return new SharedGroupOrderItem(sharedGroupOrder, line);
    }

    public long amount() {
        return unitPrice * quantity;
    }

    // 판매 중지 등으로 이 항목만 취소된 시각 (주문 전체 취소와 별개)
    private LocalDateTime canceledAt;

    public boolean isCanceled() {
        return canceledAt != null;
    }

    void cancel(LocalDateTime now) {
        if (canceledAt == null) {
            canceledAt = now;
        }
    }
}
