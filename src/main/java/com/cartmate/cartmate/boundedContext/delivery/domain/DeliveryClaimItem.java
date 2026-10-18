package com.cartmate.cartmate.boundedContext.delivery.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "delivery_claim_item",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_delivery_claim_item",
                columnNames = {"claim_id", "delivery_item_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryClaimItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claim_id", nullable = false)
    private DeliveryClaim claim;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_item_id", nullable = false)
    private DeliveryItem deliveryItem;

    @Column(name = "quantity", nullable = false)
    private Long quantity;      //교환·환불을 요청한 수량

    public static DeliveryClaimItem create(DeliveryClaim claim, DeliveryItem deliveryItem, Long quantity) {
        return new DeliveryClaimItem(claim, deliveryItem, quantity);
    }

    private DeliveryClaimItem(DeliveryClaim claim, DeliveryItem deliveryItem, Long quantity) {
        this.claim = claim;
        this.deliveryItem = deliveryItem;
        this.quantity = quantity;
    }
}
