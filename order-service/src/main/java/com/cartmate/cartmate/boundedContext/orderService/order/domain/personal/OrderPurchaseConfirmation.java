package com.cartmate.cartmate.boundedContext.orderService.order.domain.personal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "order_purchase_confirmation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderPurchaseConfirmation {

    @Id
    private Long orderId;

    @Column(nullable = false)
    private LocalDateTime confirmedAt;

    private OrderPurchaseConfirmation(Long orderId, LocalDateTime confirmedAt) {
        this.orderId = orderId;
        this.confirmedAt = confirmedAt;
    }

    public static OrderPurchaseConfirmation of(Long orderId, LocalDateTime confirmedAt) {
        return new OrderPurchaseConfirmation(orderId, confirmedAt);
    }
}
