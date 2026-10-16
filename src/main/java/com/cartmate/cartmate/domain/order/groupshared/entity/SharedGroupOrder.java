package com.cartmate.cartmate.domain.order.groupshared.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@Getter
@Entity
@Table(name = "shared_group_order")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SharedGroupOrder {

    public static final Duration PAYMENT_PERIOD = Duration.ofHours(48);
    public static final Duration GRACE_PERIOD = Duration.ofMinutes(10);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long sharedGroupPurchaseId;

    @Column(nullable = false)
    private Long creatorMemberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private SharedGroupOrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(30)")
    private SharedGroupOrderCancelReason cancelReason;

    @Column(nullable = false)
    private long totalAmount;

    @Column(nullable = false)
    private LocalDateTime paymentDeadlineAt;

    @Column(nullable = false)
    private boolean stockReserved;


    @Embedded
    private DeliveryAddress teamDeliveryAddress;

    private LocalDateTime confirmedAt;

    private LocalDateTime canceledAt;

    @OrderBy("id")
    @OneToMany(mappedBy = "sharedGroupOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SharedGroupOrderParticipant> participants = new ArrayList<>();


    @OrderBy("id")
    @OneToMany(mappedBy = "sharedGroupOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SharedGroupOrderItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;


    private SharedGroupOrder(Long sharedGroupPurchaseId, Long creatorMemberId, List<Long> participantMemberIds,
                             List<OrderItemLine> itemLines, LocalDateTime closedAt) {
        if (itemLines == null || itemLines.isEmpty()) {
            throw new IllegalArgumentException("주문할 상품이 없습니다.");
        }
        if (participantMemberIds == null || !participantMemberIds.contains(creatorMemberId)) {
            throw new IllegalArgumentException("생성자는 참여자에 포함되어야 합니다.");
        }
        if (participantMemberIds.stream().distinct().count() != participantMemberIds.size()) {
            throw new IllegalArgumentException("참여자가 중복되었습니다.");
        }
        this.sharedGroupPurchaseId = sharedGroupPurchaseId;
        this.creatorMemberId = creatorMemberId;
        this.status = SharedGroupOrderStatus.PAYING;
        this.paymentDeadlineAt = closedAt.plus(PAYMENT_PERIOD);
        this.stockReserved = false;

        itemLines.forEach(line -> items.add(SharedGroupOrderItem.create(
                this, line.productId(), line.inventoryId(), line.quantity(), line.unitPrice())));
        this.totalAmount = items.stream().mapToLong(SharedGroupOrderItem::amount).sum();


        int headCount = participantMemberIds.size();
        long perPerson = totalAmount / headCount;
        long remainder = totalAmount % headCount;
        participantMemberIds.forEach(memberId -> participants.add(SharedGroupOrderParticipant.create(
                this, memberId, memberId.equals(creatorMemberId) ? perPerson + remainder : perPerson)));
    }

    public static SharedGroupOrder create(Long sharedGroupPurchaseId, Long creatorMemberId, List<Long> participantMemberIds,
                                          List<OrderItemLine> itemLines, LocalDateTime closedAt) {
        return new SharedGroupOrder(sharedGroupPurchaseId, creatorMemberId, participantMemberIds, itemLines, closedAt);
    }


    public boolean canAcceptPayment(LocalDateTime paidAt) {
        return status == SharedGroupOrderStatus.PAYING && !paidAt.isAfter(paymentDeadlineAt);
    }

    public boolean needsStockReservation() {
        return status == SharedGroupOrderStatus.PAYING && !stockReserved;
    }

    public void markStockReserved() {
        this.stockReserved = true;
    }

    public boolean pay(Long memberId, LocalDateTime paidAt, DeliveryAddress teamDeliveryAddress) {
        if (!canAcceptPayment(paidAt)) {
            throw new IllegalArgumentException("결제할 수 없는 공동 주문입니다.");
        }
        if (!stockReserved) {
            throw new IllegalStateException("재고를 선점하지 않은 공동 주문입니다.");
        }
        SharedGroupOrderParticipant participant = getParticipant(memberId);
        if (isCreator(memberId)) {
            if (teamDeliveryAddress == null) {
                throw new IllegalArgumentException("생성자는 팀 배송지를 입력해야 합니다.");
            }
            this.teamDeliveryAddress = teamDeliveryAddress;
        }
        participant.markPaid(paidAt);

        if (participants.stream().allMatch(SharedGroupOrderParticipant::isPaid)) {
            this.status = SharedGroupOrderStatus.CONFIRMED;
            this.confirmedAt = paidAt;
            return true;
        }
        return false;
    }


    public List<RefundShare> cancelByDeadline(LocalDateTime now) {
        if (status != SharedGroupOrderStatus.PAYING || now.isBefore(paymentDeadlineAt.plus(GRACE_PERIOD))) {
            return List.of();
        }
        return cancel(SharedGroupOrderCancelReason.DEADLINE_EXPIRED, now);
    }

    public List<RefundShare> cancelByCreator(Long requesterId, LocalDateTime now) {
        validateCreator(requesterId);
        validatePaying();
        return cancel(SharedGroupOrderCancelReason.CREATOR_CANCELED, now);
    }

    public List<RefundShare> cancelByOutOfStock(LocalDateTime now) {
        validatePaying();
        return cancel(SharedGroupOrderCancelReason.OUT_OF_STOCK, now);
    }


    public List<RefundShare> refundItem(Long requesterId, Long itemId, LocalDateTime now) {
        validateCreator(requesterId);
        if (status != SharedGroupOrderStatus.CONFIRMED) {
            throw new IllegalArgumentException("확정된 공동 주문만 부분 환불할 수 있습니다.");
        }
        SharedGroupOrderItem item = items.stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("공동 주문에 없는 항목입니다."));
        item.markRefunded(now);

        long refundAmount = item.amount();
        List<RefundShare> shares = new ArrayList<>();
        long distributed = 0L;
        for (SharedGroupOrderParticipant participant : participants) {
            long share = totalAmount == 0 ? 0L : refundAmount * participant.getShareAmount() / totalAmount;
            shares.add(new RefundShare(participant.getMemberId(), share));
            distributed += share;
        }
        long remainder = refundAmount - distributed;

        List<RefundShare> result = shares.stream()
                .map(share -> share.memberId().equals(creatorMemberId)
                        ? new RefundShare(share.memberId(), share.amount() + remainder)
                        : share)
                .toList();
        result.forEach(share -> getParticipant(share.memberId()).addRefund(share.amount()));
        return result;
    }


    public boolean isCreator(Long memberId) {
        return creatorMemberId.equals(memberId);
    }

    public boolean isParticipant(Long memberId) {
        return participants.stream().anyMatch(participant -> participant.is(memberId));
    }

    public SharedGroupOrderParticipant getParticipant(Long memberId) {
        return participants.stream()
                .filter(participant -> participant.is(memberId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("공동 주문 참여자가 아닙니다."));
    }


    private List<RefundShare> cancel(SharedGroupOrderCancelReason reason, LocalDateTime now) {
        this.status = SharedGroupOrderStatus.CANCELED;
        this.cancelReason = reason;
        this.canceledAt = now;
        List<RefundShare> refunds = new ArrayList<>();
        for (SharedGroupOrderParticipant participant : participants) {
            long amount = participant.refundAll();
            if (amount > 0) {
                refunds.add(new RefundShare(participant.getMemberId(), amount));
            }
        }
        return refunds;
    }

    private void validatePaying() {
        if (status != SharedGroupOrderStatus.PAYING) {
            throw new IllegalArgumentException("결제 중인 공동 주문이 아닙니다.");
        }
    }

    private void validateCreator(Long memberId) {
        if (!isCreator(memberId)) {
            throw new IllegalArgumentException("공동 주문 생성자만 할 수 있습니다.");
        }
    }


    public record OrderItemLine(
            Long productId,
            Long inventoryId,
            int quantity,
            long unitPrice
    ) {
    }
}
