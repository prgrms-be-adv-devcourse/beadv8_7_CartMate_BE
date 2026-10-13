package com.cartmate.cartmate.domain.cart.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "group_cart_item")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupCartItem {

    private static final int MIN_QUANTITY = 1;
    private static final int MAX_QUANTITY = 999;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_cart_id", nullable = false)
    private GroupCart groupCart;

    @Column(nullable = false)
    private Long addedMemberId;

    private Long assigneeMemberId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Long inventoryId;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private CostShareType costShareType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private DeliveryTarget deliveryTarget;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private GroupCartItem(GroupCart groupCart, Long addedMemberId, Long productId, Long inventoryId,
                          int quantity, CostShareType costShareType, DeliveryTarget deliveryTarget) {
        validateQuantity(quantity);
        this.groupCart = groupCart;
        this.addedMemberId = addedMemberId;
        this.productId = productId;
        this.inventoryId = inventoryId;
        this.quantity = quantity;
        this.costShareType = costShareType;
        this.deliveryTarget = deliveryTarget;
    }

    static GroupCartItem createShared(GroupCart groupCart, Long addedMemberId, Long productId, Long inventoryId,
                                      int quantity, DeliveryTarget deliveryTarget) {
        return new GroupCartItem(groupCart, addedMemberId, productId, inventoryId,
                quantity, CostShareType.SHARED, deliveryTarget);
    }

    // 개인 부담 상품은 1개 = 1행
    static GroupCartItem createPersonal(GroupCart groupCart, Long addedMemberId, Long productId, Long inventoryId,
                                        DeliveryTarget deliveryTarget) {
        return new GroupCartItem(groupCart, addedMemberId, productId, inventoryId,
                1, CostShareType.PERSONAL, deliveryTarget);
    }

    // 복제용: 받는 사람은 복사하지 않는다
    static GroupCartItem copyOf(GroupCart groupCart, GroupCartItem source) {
        return new GroupCartItem(groupCart, source.addedMemberId, source.productId, source.inventoryId,
                source.quantity, source.costShareType, source.deliveryTarget);
    }

    public boolean isPersonal() {
        return costShareType == CostShareType.PERSONAL;
    }

    public boolean isAssigned() {
        return assigneeMemberId != null;
    }

    public boolean isAssignedTo(Long memberId) {
        return memberId.equals(assigneeMemberId);
    }

    boolean canMergeWith(Long inventoryId, DeliveryTarget deliveryTarget, Long addedMemberId) {
        return !isPersonal()
                && !isAssigned()
                && this.inventoryId.equals(inventoryId)
                && this.deliveryTarget == deliveryTarget
                && this.addedMemberId.equals(addedMemberId);
    }

    void increaseQuantity(int amount) {
        validateShared();
        validateQuantity(amount);
        validateQuantity(this.quantity + amount);
        this.quantity += amount;
    }

    void changeQuantity(int quantity) {
        validateShared();
        validateQuantity(quantity);
        this.quantity = quantity;
    }

    void assignTo(Long memberId) {
        this.assigneeMemberId = memberId;
    }

    void unassign() {
        this.assigneeMemberId = null;
    }

    void changeOption(Long productId, Long inventoryId) {
        if (!this.productId.equals(productId)) {
            throw new IllegalArgumentException("같은 상품의 옵션으로만 변경할 수 있습니다.");
        }
        this.inventoryId = inventoryId;
    }

    void changeDeliveryTarget(DeliveryTarget deliveryTarget) {
        this.deliveryTarget = deliveryTarget;
    }

    void changeToShared() {
        this.costShareType = CostShareType.SHARED;
    }

    private void validateShared() {
        if (isPersonal()) {
            throw new IllegalArgumentException("개인 부담 상품은 수량을 바꿀 수 없습니다. 상품을 추가하거나 삭제해 주세요.");
        }
    }

    private void validateQuantity(int quantity) {
        if (quantity < MIN_QUANTITY || quantity > MAX_QUANTITY) {
            throw new IllegalArgumentException("수량은 " + MIN_QUANTITY + "개 이상 " + MAX_QUANTITY + "개 이하여야 합니다.");
        }
    }
}
