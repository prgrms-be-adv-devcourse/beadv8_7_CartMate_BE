package com.cartmate.cartmate.boundedContext.orderService.order.domain.personal;

import com.cartmate.cartmate.boundedContext.orderService.order.domain.personal.Order.OrderLine;
import com.cartmate.cartmate.boundedContext.orderService.order.exception.OrderErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "order_item",
        indexes = @Index(name = "idx_order_item_order_id", columnList = "order_id"),
        uniqueConstraints = @UniqueConstraint(
                name = OrderItem.PENDING_BUYER_INVENTORY_UNIQUE,
                columnNames = {"pending_buyer_member_id", "inventory_id"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    public static final String PENDING_BUYER_INVENTORY_UNIQUE = "uk_order_item_pending_buyer_inventory";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

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
    private long unitPrice;

    @Column(nullable = false)
    private int quantity;

    // 결제 대기 중인 개인 주문일 때만 구매자 ID, 아니면 NULL. unique(pending_buyer_member_id, inventory_id)로
    // "같은 회원이 같은 재고 상품의 결제 대기 개인 주문을 둘 가질 수 없다"를 DB가 보장한다 (NULL끼리는 겹쳐도 된다)
    @Column(name = "pending_buyer_member_id")
    private Long pendingBuyerMemberId;

    private OrderItem(Order order, OrderLine line) {
        if (line.quantity() < 1) {
            throw new ServiceException(OrderErrorCode.ORDER_INVALID_QUANTITY);
        }
        if (line.unitPrice() < 0) {
            throw new ServiceException(OrderErrorCode.ORDER_INVALID_PRICE);
        }
        this.order = order;
        this.productId = line.productId();
        this.inventoryId = line.inventoryId();
        this.sellerId = line.sellerId();
        this.productName = line.productName();
        this.optionName = line.optionName();
        this.unitPrice = line.unitPrice();
        this.quantity = line.quantity();
        this.pendingBuyerMemberId = order.getOrderType() == OrderType.PERSONAL ? order.getBuyerMemberId() : null;
    }

    static OrderItem create(Order order, OrderLine line) {
        return new OrderItem(order, line);
    }

    void releasePending() {
        this.pendingBuyerMemberId = null;
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
