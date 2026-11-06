package com.cartmate.cartmate.boundedContext.orderService.cart.domain.groupindividual;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "individual_group_purchase_order",
        indexes = @Index(name = "idx_igp_order_purchase_status", columnList = "individual_group_purchase_id, status"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IndividualGroupPurchaseOrderReplica implements Persistable<Long> {

    @Id
    private Long orderId;

    private Long individualGroupPurchaseId;

    private Long buyerMemberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private IndividualGroupPurchaseOrderReplicaStatus status;

    private LocalDateTime orderedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderItemId ASC")
    private List<IndividualGroupPurchaseOrderLineReplica> lines = new ArrayList<>();

    @Transient
    private boolean newEntity = true;

    private IndividualGroupPurchaseOrderReplica(Long orderId) {
        this.orderId = orderId;
        this.status = IndividualGroupPurchaseOrderReplicaStatus.PENDING;
    }

    public static IndividualGroupPurchaseOrderReplica create(Long orderId) {
        return new IndividualGroupPurchaseOrderReplica(orderId);
    }

    public void applyCreated(Long individualGroupPurchaseId, Long buyerMemberId, LocalDateTime createdAt, List<Line> items) {
        fill(individualGroupPurchaseId, buyerMemberId, items);
        this.orderedAt = createdAt;
    }

    public void applyPaid(Long individualGroupPurchaseId, Long buyerMemberId, LocalDateTime paidAt, List<Line> items) {
        fill(individualGroupPurchaseId, buyerMemberId, items);
        if (this.orderedAt == null) {
            this.orderedAt = paidAt;
        }
        if (this.status != IndividualGroupPurchaseOrderReplicaStatus.CANCELED) {
            this.status = IndividualGroupPurchaseOrderReplicaStatus.PAID;
        }
    }

    public void applyCanceled() {
        this.status = IndividualGroupPurchaseOrderReplicaStatus.CANCELED;
    }

    public void applySellerItemsCanceled(Long sellerId) {
        lines.stream().filter(line -> line.isSoldBy(sellerId)).forEach(IndividualGroupPurchaseOrderLineReplica::cancel);
        if (!lines.isEmpty() && lines.stream().allMatch(IndividualGroupPurchaseOrderLineReplica::isCanceled)) {
            this.status = IndividualGroupPurchaseOrderReplicaStatus.CANCELED;
        }
    }

    private void fill(Long individualGroupPurchaseId, Long buyerMemberId, List<Line> items) {
        if (this.individualGroupPurchaseId == null) {
            this.individualGroupPurchaseId = individualGroupPurchaseId;
        }
        if (this.buyerMemberId == null) {
            this.buyerMemberId = buyerMemberId;
        }
        if (this.lines.isEmpty()) {
            items.forEach(item -> this.lines.add(IndividualGroupPurchaseOrderLineReplica.create(this, item)));
        }
    }

    @Override
    public Long getId() {
        return orderId;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.newEntity = false;
    }

    public record Line(Long orderItemId, Long productId, Long inventoryId, Long sellerId, int quantity, long unitPrice) {
    }
}
