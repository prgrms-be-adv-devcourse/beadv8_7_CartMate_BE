package com.cartmate.cartmate.boundedContext.orderService.order.domain.personal;

import com.cartmate.cartmate.boundedContext.orderService.order.exception.OrderErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "individual_group_purchase_replica")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IndividualGroupPurchaseReplica implements Persistable<Long> {

    @Id
    private Long individualGroupPurchaseId;

    @Column(nullable = false)
    private LocalDateTime deadlineAt;

    @Column(nullable = false)
    private boolean closed;

    private LocalDateTime closedAt;

    @Column(nullable = false)
    private long revision;

    @ElementCollection
    @CollectionTable(name = "individual_group_purchase_replica_product",
            joinColumns = @JoinColumn(name = "individual_group_purchase_id"))
    @Column(name = "product_id", nullable = false)
    private Set<Long> productIds = new HashSet<>();

    @Transient
    private boolean newEntity = true;

    private IndividualGroupPurchaseReplica(Long individualGroupPurchaseId, LocalDateTime deadlineAt, boolean closed,
                                           LocalDateTime closedAt, Collection<Long> productIds, long revision) {
        this.individualGroupPurchaseId = individualGroupPurchaseId;
        this.deadlineAt = deadlineAt;
        this.closed = closed;
        this.closedAt = closedAt;
        this.productIds.addAll(productIds);
        this.revision = revision;
    }

    public static IndividualGroupPurchaseReplica of(Long individualGroupPurchaseId, LocalDateTime deadlineAt,
                                                    boolean closed, LocalDateTime closedAt,
                                                    Collection<Long> productIds, long revision) {
        return new IndividualGroupPurchaseReplica(individualGroupPurchaseId, deadlineAt, closed, closedAt, productIds,
                revision);
    }

    public boolean applyOpenState(LocalDateTime deadlineAt, Collection<Long> productIds, long revision) {
        if (closed || revision <= this.revision) {
            return false;
        }
        this.deadlineAt = deadlineAt;
        this.productIds.clear();
        this.productIds.addAll(productIds);
        this.revision = revision;
        return true;
    }

    public boolean applyClosed(LocalDateTime deadlineAt, LocalDateTime closedAt, long revision) {
        if (closed) {
            return false;
        }
        this.closed = true;
        this.closedAt = closedAt;
        this.deadlineAt = deadlineAt;
        this.revision = Math.max(this.revision, revision);
        return true;
    }

    public boolean isOpen(LocalDateTime now) {
        return !closed && now.isBefore(deadlineAt);
    }

    public void validateOrderable(List<Long> orderProductIds, LocalDateTime now) {
        if (!isOpen(now)) {
            throw new ServiceException(OrderErrorCode.ORDER_GROUP_PURCHASE_SALE_CLOSED);
        }
        if (!productIds.containsAll(orderProductIds)) {
            throw new ServiceException(OrderErrorCode.ORDER_GROUP_PURCHASE_PRODUCT_NOT_SOLD);
        }
    }

    public boolean canAcceptPayment(List<Long> orderProductIds, LocalDateTime now) {
        return isOpen(now) && productIds.containsAll(orderProductIds);
    }

    @Override
    public Long getId() {
        return individualGroupPurchaseId;
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
}
