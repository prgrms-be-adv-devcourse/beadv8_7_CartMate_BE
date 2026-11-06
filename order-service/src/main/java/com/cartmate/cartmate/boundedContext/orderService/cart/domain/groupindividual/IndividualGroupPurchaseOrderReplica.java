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

/**
 * 개인물품 공동구매 주문의 cart 쪽 사본(읽기 모델). order가 아웃박스로 보낸 주문 생성 · 결제 · 취소 이벤트로만 바뀐다.
 * 현황판이 order 테이블을 직접 읽지 않게 한다.
 *
 * <p>릴레이는 순서와 한 번 전달을 보장하지 않는다. 그래서 어느 이벤트가 먼저 와도 이 행을 만들고,
 * 상태는 앞으로만 간다. 취소가 먼저 오면 공동구매 ID를 모른 채 취소 행만 남고, 나중에 온 생성 · 결제 이벤트가 정보를 채우되 상태는 바꾸지 않는다.
 */
@Entity
@Table(name = "individual_group_purchase_order",
        indexes = @Index(name = "idx_igp_order_purchase_status", columnList = "individual_group_purchase_id, status"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IndividualGroupPurchaseOrderReplica implements Persistable<Long> {

    // order 모듈의 주문 ID를 그대로 쓴다
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

    /**
     * 판매 중지로 결제 완료 주문에서 한 판매자 항목만 취소됐다. 남은 항목이 없으면 주문도 취소다.
     * order는 이 경우 이중 환불을 막으려고 주문 취소 이벤트를 따로 보내지 않으므로, 여기서 주문 취소까지 판단한다.
     */
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
