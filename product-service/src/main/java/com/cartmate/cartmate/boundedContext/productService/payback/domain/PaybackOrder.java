package com.cartmate.cartmate.boundedContext.productService.payback.domain;

import com.cartmate.cartmate.shared.order.OrderStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Entity
@Table(name = "product_payback_order",
        indexes = @Index(name = "idx_product_payback_order_purchase", columnList = "individual_group_purchase_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaybackOrder {

    @Id
    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "individual_group_purchase_id")
    private Long individualGroupPurchaseId;

    private Long buyerMemberId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private OrderStatus status;

    @ElementCollection
    @BatchSize(size = 100)
    @CollectionTable(name = "product_payback_order_item", joinColumns = @JoinColumn(name = "order_id"))
    private List<PaybackOrderItem> items = new ArrayList<>();

    @ElementCollection
    @BatchSize(size = 100)
    @CollectionTable(name = "product_payback_order_canceled_seller", joinColumns = @JoinColumn(name = "order_id"))
    @Column(name = "seller_id", nullable = false)
    private Set<Long> canceledSellerIds = new HashSet<>();

    @ElementCollection
    @BatchSize(size = 100)
    @CollectionTable(name = "product_payback_order_confirmed_seller", joinColumns = @JoinColumn(name = "order_id"))
    @Column(name = "seller_id", nullable = false)
    private Set<Long> confirmedSellerIds = new HashSet<>();

    PaybackOrder(Long orderId) {
        this.orderId = orderId;
    }

    public void applyPlaced(Long individualGroupPurchaseId, Long buyerMemberId, Collection<PaybackOrderItem> items,
                            OrderStatus status) {
        if (this.individualGroupPurchaseId == null) {
            this.individualGroupPurchaseId = individualGroupPurchaseId;
        }
        if (this.buyerMemberId == null) {
            this.buyerMemberId = buyerMemberId;
        }
        if (this.items.isEmpty() && items != null) {
            this.items.addAll(items);
        }
        advance(status);
    }

    public void applyCanceled() {
        advance(OrderStatus.CANCELED);
    }

    public void applySellerItemsCanceled(Long sellerId) {
        canceledSellerIds.add(sellerId);
    }

    public void applyConfirmed(Long sellerId) {
        confirmedSellerIds.add(sellerId);
    }

    public boolean isCanceled() {
        return status == OrderStatus.CANCELED;
    }

    public boolean isPaid() {
        return status == OrderStatus.PAID;
    }

    public boolean isSellerCanceled(Long sellerId) {
        return canceledSellerIds.contains(sellerId);
    }

    public boolean isSellerConfirmed(Long sellerId) {
        return confirmedSellerIds.contains(sellerId);
    }

    private void advance(OrderStatus next) {
        if (rank(next) > rank(status)) {
            this.status = next;
        }
    }

    private static int rank(OrderStatus status) {
        if (status == null) {
            return 0;
        }
        return switch (status) {
            case PAYMENT_PENDING -> 1;
            case PAID -> 2;
            case CANCELED -> 3;
        };
    }
}
