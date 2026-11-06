package com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "individual_group_purchase_order_line")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IndividualGroupPurchaseOrderLineReplica {

    @Id
    private Long orderItemId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private IndividualGroupPurchaseOrderReplica order;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Long inventoryId;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private long unitPrice;

    private Long sellerId;

    @Column(nullable = false)
    private boolean canceled;

    static IndividualGroupPurchaseOrderLineReplica create(IndividualGroupPurchaseOrderReplica order,
                                                         IndividualGroupPurchaseOrderReplica.Line line) {
        IndividualGroupPurchaseOrderLineReplica replica = new IndividualGroupPurchaseOrderLineReplica();
        replica.orderItemId = line.orderItemId();
        replica.order = order;
        replica.productId = line.productId();
        replica.inventoryId = line.inventoryId();
        replica.quantity = line.quantity();
        replica.unitPrice = line.unitPrice();
        replica.sellerId = line.sellerId();
        return replica;
    }

    void cancel() {
        this.canceled = true;
    }

    boolean isSoldBy(Long sellerId) {
        return sellerId != null && sellerId.equals(this.sellerId);
    }
}
