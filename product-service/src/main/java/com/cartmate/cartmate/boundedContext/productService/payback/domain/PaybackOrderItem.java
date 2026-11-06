package com.cartmate.cartmate.boundedContext.productService.payback.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaybackOrderItem {

    @Column(nullable = false)
    private Long orderItemId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Long sellerId;

    @Column(nullable = false)
    private int quantity;

    private PaybackOrderItem(Long orderItemId, Long productId, Long sellerId, int quantity) {
        this.orderItemId = orderItemId;
        this.productId = productId;
        this.sellerId = sellerId;
        this.quantity = quantity;
    }

    public static PaybackOrderItem of(Long orderItemId, Long productId, Long sellerId, int quantity) {
        return new PaybackOrderItem(orderItemId, productId, sellerId, quantity);
    }
}
