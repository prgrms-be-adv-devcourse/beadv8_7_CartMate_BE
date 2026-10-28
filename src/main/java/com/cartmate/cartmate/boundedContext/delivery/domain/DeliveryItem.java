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

    @Column(name = "unit_price", nullable = false)
    private Long unitPrice;         //주문 시점의 단가 스냅샷. 옵션 가격이 반영된 최종 단가이며 정산 금액 계산에 쓴다.

    @Column(name = "quantity", nullable = false)
    private Long quantity;

    public static DeliveryItem create(
            Delivery delivery, Long orderItemId, String productName, Long unitPrice, Long quantity
    ) {
        return new DeliveryItem(delivery, orderItemId, productName, unitPrice, quantity);
    }

    private DeliveryItem(
            Delivery delivery,
            Long orderItemId,
            String productName,
            Long unitPrice,
            Long quantity
    ) {
        this.delivery = delivery;
        this.orderItemId = orderItemId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }
}
