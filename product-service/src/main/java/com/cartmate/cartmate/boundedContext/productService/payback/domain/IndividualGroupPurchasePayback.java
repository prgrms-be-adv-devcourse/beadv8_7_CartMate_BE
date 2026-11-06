package com.cartmate.cartmate.boundedContext.productService.payback.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Getter
@Entity
@Table(name = "product_individual_group_purchase_payback")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IndividualGroupPurchasePayback {

    @Id
    @Column(name = "individual_group_purchase_id")
    private Long individualGroupPurchaseId;

    @Column(nullable = false)
    private boolean opened;

    private LocalDateTime deadlineAt;

    @Column(nullable = false)
    private boolean closed;

    private LocalDateTime closedAt;

    @Column(nullable = false)
    private long revision;

    @ElementCollection
    @BatchSize(size = 100)
    @CollectionTable(name = "product_individual_group_purchase_payback_product",
            joinColumns = @JoinColumn(name = "individual_group_purchase_id"))
    @Column(name = "product_id", nullable = false)
    private Set<Long> enteredProductIds = new HashSet<>();

    @ElementCollection
    @BatchSize(size = 100)
    @CollectionTable(name = "product_individual_group_purchase_payback_condition",
            joinColumns = @JoinColumn(name = "individual_group_purchase_id"))
    private Set<PaybackConditionSnapshot> conditionSnapshots = new HashSet<>();

    private LocalDateTime judgedAt;

    @Column(nullable = false)
    private boolean paid;

    @ElementCollection
    @CollectionTable(name = "product_individual_group_purchase_payback_judgment",
            joinColumns = @JoinColumn(name = "individual_group_purchase_id"))
    @OrderColumn(name = "line_no")
    private List<PaybackConditionJudgment> conditionJudgments = new ArrayList<>();

    public Set<Long> applyOpenState(LocalDateTime deadlineAt, Collection<Long> productIds, long revision) {
        this.opened = true;
        if (!closed && (this.deadlineAt == null || revision > this.revision)) {
            this.deadlineAt = deadlineAt;
            this.revision = Math.max(this.revision, revision);
        }
        return enter(productIds);
    }

    public void applyClosed(LocalDateTime deadlineAt, LocalDateTime closedAt, long revision) {
        if (closed) {
            return;
        }
        this.closed = true;
        this.closedAt = closedAt;
        this.deadlineAt = deadlineAt;
        this.revision = Math.max(this.revision, revision);
    }

    public void snapshot(Collection<PaybackConditionSnapshot> snapshots) {
        snapshots.stream()
                .filter(snapshot -> enteredProductIds.contains(snapshot.getProductId()))
                .forEach(conditionSnapshots::add);
    }

    public boolean hasCondition() {
        return !conditionSnapshots.isEmpty();
    }

    public boolean isJudged() {
        return judgedAt != null;
    }

    public boolean isJudgeable(LocalDateTime now) {
        return !isJudged() && hasCondition() && isEnded(now);
    }

    public void judge(PaybackSettlement.Result result, LocalDateTime now) {
        if (isJudged()) {
            throw new IllegalStateException("payback already judged: " + individualGroupPurchaseId);
        }
        this.judgedAt = now;
        this.paid = result.paid();
        result.conditionResults().stream()
                .map(PaybackConditionJudgment::from)
                .forEach(conditionJudgments::add);
    }

    public boolean isEnded(LocalDateTime now) {
        return opened && (closed || (deadlineAt != null && !now.isBefore(deadlineAt)));
    }

    private Set<Long> enter(Collection<Long> productIds) {
        Set<Long> entered = new LinkedHashSet<>();
        if (productIds == null) {
            return entered;
        }
        productIds.stream()
                .filter(enteredProductIds::add)
                .forEach(entered::add);
        return entered;
    }
}
