package com.cartmate.cartmate.boundedContext.order.domain.personal;

import com.cartmate.cartmate.boundedContext.order.exception.OrderErrorCode;
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

/**
 * 개인물품 공동구매의 order 쪽 사본(읽기 모델). cart가 아웃박스로 보낸 열림 · 갱신 · 종료 이벤트로만 바뀐다.
 * 주문 생성과 결제가 cart 공동구매 행을 잠그거나 동기로 묻지 않고 여기서 판단한다(ARCH-16).
 *
 * <p>릴레이는 순서와 한 번 전달을 보장하지 않는다. 그래서 cart가 붙인 {@code revision}이 지금보다 클 때만 덮어쓰고,
 * 종료는 끝 상태라 한 번 닫히면 늦게 온 열림 · 갱신이 다시 열지 못한다.
 *
 * <p>사본은 cart보다 1~2초 늦을 수 있다. 그 틈에 종료 · 상품 제거 뒤의 주문이 생길 수 있어서,
 * 결제 때 {@link #wasSellingAt}으로 "주문을 만든 순간 팔고 있었는가"를 다시 본다.
 */
@Entity
@Table(name = "individual_group_purchase_replica")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IndividualGroupPurchaseReplica implements Persistable<Long> {

    // cart 모듈의 공동구매 ID를 그대로 쓴다
    @Id
    private Long individualGroupPurchaseId;

    @Column(nullable = false)
    private LocalDateTime deadlineAt;

    // cart DB 상태가 CLOSED인가. 회원 공동구매는 마감 시각이 지나도 닫힘 이벤트가 없으므로 deadlineAt과 함께 판단한다
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

    /**
     * 열림 · 갱신 이벤트(바뀐 뒤 전체 상태)를 반영한다. 닫혔거나 더 새 상태를 이미 받았으면 버린다.
     *
     * @return 반영했으면 true
     */
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

    /**
     * 종료 이벤트를 반영한다. 끝 상태라 번호가 작아도 닫는다(닫힌 뒤 cart는 더 바꾸지 않는다). 상품 목록은 그대로 둔다.
     *
     * @return 이번에 닫았으면 true
     */
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

    // 주문 생성 판단. cart의 IndividualGroupPurchase.validateOrderable과 같은 규칙이다
    public void validateOrderable(List<Long> orderProductIds, LocalDateTime now) {
        if (!isOpen(now)) {
            throw new ServiceException(OrderErrorCode.ORDER_GROUP_PURCHASE_SALE_CLOSED);
        }
        if (!productIds.containsAll(orderProductIds)) {
            throw new ServiceException(OrderErrorCode.ORDER_GROUP_PURCHASE_PRODUCT_NOT_SOLD);
        }
    }

    /**
     * 주문을 만든 순간({@code orderedAt}) 이 공동구매가 그 상품들을 팔고 있었는가. 결제 때 다시 확인한다.
     * 사본이 늦어 종료 · 마감 뒤에 생긴 주문은 false다. 종료 전에 만든 주문은 종료 뒤에도 true
     * (예전부터 결제 기한까지는 결제할 수 있었다).
     *
     * <p>상품 목록은 열려 있는 공동구매에서만 본다. 개설자의 상품 제거는 그 상품의 결제 대기 · 완료 주문이 없을 때만 되므로,
     * 지금 목록에 없는 상품이 든 결제 대기 주문은 제거와 겹쳐 생긴 주문이다. 닫힌 사본은 목록을 믿지 않는다:
     * 종료 이벤트에는 상품 목록이 없고(먼저 오면 빈 목록), 판매 중지로 닫히면 cart 목록이 비며, 닫힌 뒤엔 갱신을 받지 않는다.
     * 판매 중지된 상품은 이 검사 앞의 상품 판매 가능 재확인(ORDER_PRODUCT_NOT_PURCHASABLE)이 따로 막는다.
     *
     * <p>한계: 시각 비교는 order의 주문 생성 시각과 cart의 종료 시각을 견준다. 지금은 한 JVM이라 같은 시계다.
     * 서버가 나뉘면 시계 차만큼 경계가 흐려진다(ARCH-12 · 13에서 다시 본다).
     */
    public boolean wasSellingAt(List<Long> orderProductIds, LocalDateTime orderedAt) {
        boolean openAtOrder = orderedAt.isBefore(deadlineAt) && (closedAt == null || orderedAt.isBefore(closedAt));
        return openAtOrder && (closed || productIds.containsAll(orderProductIds));
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
