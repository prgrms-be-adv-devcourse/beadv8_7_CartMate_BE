package com.cartmate.cartmate.boundedContext.orderService.order.domain.groupshared;

import com.cartmate.cartmate.shared.delivery.DeliveryCancelReason;
import com.cartmate.cartmate.shared.order.SharedGroupOrderCancelReason;
import com.cartmate.cartmate.shared.order.SharedGroupOrderStatus;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.DeliveryAddress;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.boundedContext.orderService.order.exception.OrderErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
            throw new ServiceException(OrderErrorCode.ORDER_NO_ITEMS);
        }
        if (participantMemberIds == null || !participantMemberIds.contains(creatorMemberId)) {
            throw new ServiceException(OrderErrorCode.ORDER_CREATOR_NOT_IN_PARTICIPANTS);
        }
        if (participantMemberIds.stream().distinct().count() != participantMemberIds.size()) {
            throw new ServiceException(OrderErrorCode.ORDER_DUPLICATE_PARTICIPANTS);
        }
        this.sharedGroupPurchaseId = sharedGroupPurchaseId;
        this.creatorMemberId = creatorMemberId;
        this.status = SharedGroupOrderStatus.PAYING;
        this.paymentDeadlineAt = closedAt.plus(PAYMENT_PERIOD);
        this.stockReserved = false;

        itemLines.forEach(line -> items.add(SharedGroupOrderItem.create(this, line)));
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

    public boolean pay(Long memberId, Long paymentId, LocalDateTime paidAt, DeliveryAddress teamDeliveryAddress) {
        if (paymentId == null) {
            throw new ServiceException(OrderErrorCode.ORDER_PAYMENT_ID_REQUIRED);
        }
        SharedGroupOrderParticipant participant = getParticipant(memberId);
        if (participant.isPaidWith(paymentId)) {
            return false;
        }
        if (!canAcceptPayment(paidAt)) {
            throw new ServiceException(OrderErrorCode.ORDER_NOT_PAYABLE);
        }
        if (!stockReserved) {
            throw new ServiceException(OrderErrorCode.ORDER_STOCK_NOT_RESERVED);
        }
        if (isCreator(memberId)) {
            if (teamDeliveryAddress == null) {
                throw new ServiceException(OrderErrorCode.ORDER_TEAM_ADDRESS_REQUIRED);
            }
            this.teamDeliveryAddress = teamDeliveryAddress;
        }
        participant.markPaid(paymentId, paidAt);

        if (participants.stream().allMatch(SharedGroupOrderParticipant::isPaid)) {
            this.status = SharedGroupOrderStatus.CONFIRMED;
            this.confirmedAt = paidAt;
            return true;
        }
        return false;
    }

    public boolean cancelByDeadline(LocalDateTime now) {
        if (status != SharedGroupOrderStatus.PAYING || now.isBefore(paymentDeadlineAt.plus(GRACE_PERIOD))) {
            return false;
        }
        cancel(SharedGroupOrderCancelReason.DEADLINE_EXPIRED, now);
        return true;
    }

    public boolean cancelByCreator(Long requesterId, LocalDateTime now) {
        validateCreator(requesterId);
        validatePaying();
        cancel(SharedGroupOrderCancelReason.CREATOR_CANCELED, now);
        return true;
    }

    public boolean cancelByOutOfStock(LocalDateTime now) {
        validatePaying();
        cancel(SharedGroupOrderCancelReason.OUT_OF_STOCK, now);
        return true;
    }

    public boolean cancelBySalesStopped(LocalDateTime now) {
        if (status == SharedGroupOrderStatus.CANCELED) {
            return false;
        }
        cancel(SharedGroupOrderCancelReason.SALES_STOPPED, now);
        return true;
    }

    // 확정된 공동 주문에서 한 판매자의 남은 항목을 모두 취소하고 그 금액을 돌려준다.
    // 항목이 모두 취소되면 주문도 취소로 바꾼다. 이때 환불은 전체 취소 환불로 한다(마지막 판매자 몫을 따로 나누지 않음).
    public long cancelSellerItems(Long sellerId, DeliveryCancelReason reason, LocalDateTime now) {
        if (status != SharedGroupOrderStatus.CONFIRMED) {
            return 0;
        }
        long amount = 0;
        for (SharedGroupOrderItem item : items) {
            if (sellerId.equals(item.getSellerId()) && !item.isCanceled()) {
                item.cancel(reason, now);
                amount += item.amount();
            }
        }
        if (amount > 0 && items.stream().allMatch(SharedGroupOrderItem::isCanceled)) {
            cancel(reason == DeliveryCancelReason.SALES_STOPPED
                    ? SharedGroupOrderCancelReason.SALES_STOPPED : SharedGroupOrderCancelReason.SELLER_CANCELED, now);
        }
        return amount;
    }

    // 전체 취소보다 먼저 판매자 몫으로 따로 취소된 항목을 판매자별 금액으로 묶는다
    public Map<Long, Long> canceledAmountsBySeller() {
        Map<Long, Long> bySeller = new LinkedHashMap<>();
        items.stream()
                .filter(SharedGroupOrderItem::isCanceled)
                .forEach(item -> bySeller.merge(item.getSellerId(), item.amount(), Long::sum));
        return bySeller;
    }

    public boolean isCreator(Long memberId) {
        return creatorMemberId.equals(memberId);
    }

    public boolean isParticipant(Long memberId) {
        return participants.stream().anyMatch(participant -> participant.is(memberId));
    }

    public List<Long> getUnpaidMemberIds() {
        return participants.stream()
                .filter(participant -> !participant.isPaid())
                .map(SharedGroupOrderParticipant::getMemberId)
                .toList();
    }

    public SharedGroupOrderParticipant getParticipant(Long memberId) {
        return participants.stream()
                .filter(participant -> participant.is(memberId))
                .findFirst()
                .orElseThrow(() -> new ServiceException(OrderErrorCode.ORDER_NOT_PARTICIPANT));
    }

    private void cancel(SharedGroupOrderCancelReason reason, LocalDateTime now) {
        this.status = SharedGroupOrderStatus.CANCELED;
        this.cancelReason = reason;
        this.canceledAt = now;
    }

    private void validatePaying() {
        if (status != SharedGroupOrderStatus.PAYING) {
            throw new ServiceException(OrderErrorCode.ORDER_NOT_PAYING);
        }
    }

    private void validateCreator(Long memberId) {
        if (!isCreator(memberId)) {
            throw new ServiceException(OrderErrorCode.ORDER_CREATOR_ONLY);
        }
    }

    public record OrderItemLine(
            Long productId,
            Long inventoryId,
            Long sellerId,
            String productName,
            String optionName,
            int quantity,
            long unitPrice
    ) {
    }
}
