package com.cartmate.cartmate.boundedContext.cart.domain.groupshared;

import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.boundedContext.cart.exception.CartErrorCode;
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
        name = "shared_group_purchase_item",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_shared_group_purchase_item_inventory",
                columnNames = {"shared_group_purchase_id", "inventory_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SharedGroupPurchaseItem {

    private static final int MIN_QUANTITY = 1;
    private static final int MAX_QUANTITY = 999;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shared_group_purchase_id", nullable = false)
    private SharedGroupPurchase sharedGroupPurchase;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Long inventoryId;

    @Column(nullable = false)
    private int quantity;


    @Column(nullable = false)
    private Long updatedMemberId;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;


    private SharedGroupPurchaseItem(SharedGroupPurchase sharedGroupPurchase, Long memberId,
                                    Long productId, Long inventoryId, int quantity) {
        validateQuantity(quantity);
        this.sharedGroupPurchase = sharedGroupPurchase;
        this.updatedMemberId = memberId;
        this.productId = productId;
        this.inventoryId = inventoryId;
        this.quantity = quantity;
    }

    static SharedGroupPurchaseItem create(SharedGroupPurchase sharedGroupPurchase, Long memberId,
                                          Long productId, Long inventoryId, int quantity) {
        return new SharedGroupPurchaseItem(sharedGroupPurchase, memberId, productId, inventoryId, quantity);
    }


    static SharedGroupPurchaseItem copyOf(SharedGroupPurchase sharedGroupPurchase, SharedGroupPurchaseItem source,
                                          Long memberId) {
        return new SharedGroupPurchaseItem(sharedGroupPurchase, memberId,
                source.productId, source.inventoryId, source.quantity);
    }


    boolean isSameInventory(Long inventoryId) {
        return this.inventoryId.equals(inventoryId);
    }


    void increaseQuantity(int amount, Long memberId) {
        validateQuantity(amount);
        validateQuantity(this.quantity + amount);
        this.quantity += amount;
        this.updatedMemberId = memberId;
    }

    void changeQuantity(int quantity, Long memberId) {
        validateQuantity(quantity);
        this.quantity = quantity;
        this.updatedMemberId = memberId;
    }


    void changeOption(Long productId, Long inventoryId, Long memberId) {
        validateSameProduct(productId);
        this.inventoryId = inventoryId;
        this.updatedMemberId = memberId;
    }


    void validateSameProduct(Long productId) {
        if (!this.productId.equals(productId)) {
            throw new ServiceException(CartErrorCode.CART_SHARED_ITEM_OPTION_MISMATCH);
        }
    }


    private void validateQuantity(int quantity) {
        if (quantity < MIN_QUANTITY || quantity > MAX_QUANTITY) {
            throw new ServiceException(CartErrorCode.CART_INVALID_QUANTITY);
        }
    }
}
