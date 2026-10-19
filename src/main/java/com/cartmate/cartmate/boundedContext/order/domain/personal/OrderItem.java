package com.cartmate.cartmate.boundedContext.order.domain.personal;

import com.cartmate.cartmate.boundedContext.order.domain.personal.Order.OrderLine;
import com.cartmate.cartmate.boundedContext.order.exception.OrderErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "order_item", indexes = @Index(name = "idx_order_item_order_id", columnList = "order_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

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
    }

    static OrderItem create(Order order, OrderLine line) {
        return new OrderItem(order, line);
    }

    public long amount() {
        return unitPrice * quantity;
    }
}
