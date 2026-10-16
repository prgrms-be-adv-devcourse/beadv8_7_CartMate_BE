package com.cartmate.cartmate.boundedContext.cart.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "cart_item",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cart_item_cart_inventory",
                columnNames = {"cart_id", "inventory_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem {

    private static final int MIN_QUANTITY = 1;
    private static final int MAX_QUANTITY = 999;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Long inventoryId;

    @Column(nullable = false)
    private int quantity;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private CartItem(Cart cart, Long productId, Long inventoryId, int quantity) {
        validateQuantity(quantity);
        this.cart = cart;
        this.productId = productId;
        this.inventoryId = inventoryId;
        this.quantity = quantity;
    }

    static CartItem create(Cart cart, Long productId, Long inventoryId, int quantity) {
        return new CartItem(cart, productId, inventoryId, quantity);
    }

    public void changeQuantity(int quantity) {
        validateQuantity(quantity);
        this.quantity = quantity;
    }

    void increaseQuantity(int amount) {
        validateQuantity(amount);
        validateQuantity(this.quantity + amount);
        this.quantity += amount;
    }

    private void validateQuantity(int quantity) {
        if (quantity < MIN_QUANTITY || quantity > MAX_QUANTITY) {
            throw new IllegalArgumentException("수량은 " + MIN_QUANTITY + "개 이상 " + MAX_QUANTITY + "개 이하여야 합니다.");
        }
    }
}
