package com.cartmate.cartmate.boundedContext.delivery.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "delivery_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class DeliveryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_id", nullable = false)
    private Delivery delivery;

    @Column(name = "order_item_id", nullable = false)
    private Long orderItemId;

    @Column(name = "product_name")
    private String productName;     //주문 시점의 상품명 스냅샷

    @Column(name = "quantity", nullable = false)
    private Long quantity;

    public static DeliveryItem create(Delivery delivery, Long orderItemId, Long quantity) {
        return new DeliveryItem(delivery, orderItemId, null, quantity);
    }

    public static DeliveryItem create(Delivery delivery, Long orderItemId, String productName, Long quantity) {
        return new DeliveryItem(delivery, orderItemId, productName, quantity);
    }

    private DeliveryItem(
            Delivery delivery,
            Long orderItemId,
            String productName,
            Long quantity
    ) {
        this.delivery = delivery;
        this.orderItemId = orderItemId;
        this.productName = productName;
        this.quantity = quantity;
    }
}
