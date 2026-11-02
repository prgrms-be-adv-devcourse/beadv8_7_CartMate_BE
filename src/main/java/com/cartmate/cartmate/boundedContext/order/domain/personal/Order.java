package com.cartmate.cartmate.boundedContext.order.domain.personal;

import com.cartmate.cartmate.boundedContext.order.domain.DeliveryAddress;
import com.cartmate.cartmate.boundedContext.order.exception.OrderErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
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
@Table(
        name = "orders",
        uniqueConstraints = @UniqueConstraint(name = "uk_orders_order_number", columnNames = "order_number"),
        indexes = {
                @Index(name = "idx_orders_individual_group_purchase_status", columnList = "individual_group_purchase_id, status"),
                @Index(name = "idx_orders_buyer_created", columnList = "buyer_member_id, created_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    public static final Duration PAYMENT_PERIOD = Duration.ofMinutes(30);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private OrderType orderType;

    @Column(nullable = false)
    private Long buyerMemberId;

    private Long individualGroupPurchaseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(30)")
    private OrderCancelReason cancelReason;

    @Column(nullable = false)
    private long totalAmount;

    @Column(nullable = false)
    private LocalDateTime paymentDeadlineAt;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "receiverName", column = @Column(name = "receiver_name", length = 50, nullable = false)),
            @AttributeOverride(name = "receiverPhone", column = @Column(name = "receiver_phone", length = 20, nullable = false)),
            @AttributeOverride(name = "zipCode", column = @Column(name = "zip_code", length = 10, nullable = false)),
            @AttributeOverride(name = "address", column = @Column(name = "address", length = 255, nullable = false))
    })
    private DeliveryAddress deliveryAddress;

    private Long paymentId;

    private LocalDateTime paidAt;

    private LocalDateTime canceledAt;

    @OrderBy("id")
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private Order(String orderNumber, OrderType orderType, Long buyerMemberId, Long individualGroupPurchaseId,
                  List<OrderLine> lines, DeliveryAddress deliveryAddress, LocalDateTime paymentDeadlineAt) {
        if (lines == null || lines.isEmpty()) {
            throw new ServiceException(OrderErrorCode.ORDER_NO_ITEMS);
        }
        if (deliveryAddress == null) {
            throw new ServiceException(OrderErrorCode.ORDER_INVALID_DELIVERY_ADDRESS);
        }
        this.orderNumber = orderNumber;
        this.orderType = orderType;
        this.buyerMemberId = buyerMemberId;
        this.individualGroupPurchaseId = individualGroupPurchaseId;
        this.status = OrderStatus.PAYMENT_PENDING;
        this.paymentDeadlineAt = paymentDeadlineAt;
        this.deliveryAddress = deliveryAddress;

        lines.forEach(line -> items.add(OrderItem.create(this, line)));
        this.totalAmount = items.stream().mapToLong(OrderItem::amount).sum();
    }

    public static Order createPersonal(String orderNumber, Long buyerMemberId, List<OrderLine> lines,
                                       DeliveryAddress deliveryAddress, LocalDateTime now) {
        return new Order(orderNumber, OrderType.PERSONAL, buyerMemberId, null,
                lines, deliveryAddress, now.plus(PAYMENT_PERIOD));
    }

    public static Order createIndividualGroup(String orderNumber, Long buyerMemberId, Long individualGroupPurchaseId,
                                              LocalDateTime saleDeadlineAt, List<OrderLine> lines,
                                              DeliveryAddress deliveryAddress, LocalDateTime now) {
        LocalDateTime paymentDeadlineAt = now.plus(PAYMENT_PERIOD);
        if (saleDeadlineAt.isBefore(paymentDeadlineAt)) {
            paymentDeadlineAt = saleDeadlineAt;
        }
        return new Order(orderNumber, OrderType.INDIVIDUAL_GROUP, buyerMemberId, individualGroupPurchaseId,
                lines, deliveryAddress, paymentDeadlineAt);
    }

    public boolean canAcceptPayment(LocalDateTime paidAt) {
        return status == OrderStatus.PAYMENT_PENDING && !paidAt.isAfter(paymentDeadlineAt);
    }

    public boolean markPaid(Long paymentId, LocalDateTime paidAt) {
        if (paymentId == null) {
            throw new ServiceException(OrderErrorCode.ORDER_PAYMENT_ID_REQUIRED);
        }
        if (status == OrderStatus.PAID && paymentId.equals(this.paymentId)) {
            return false;
        }
        validatePaymentPending();
        if (!canAcceptPayment(paidAt)) {
            throw new ServiceException(OrderErrorCode.ORDER_PAYMENT_EXPIRED);
        }
        this.status = OrderStatus.PAID;
        this.paymentId = paymentId;
        this.paidAt = paidAt;
        return true;
    }

    public boolean cancelByBuyer(Long requesterId, LocalDateTime now) {
        if (!isBuyer(requesterId)) {
            throw new ServiceException(OrderErrorCode.ORDER_BUYER_ONLY);
        }
        if (status == OrderStatus.CANCELED) {
            throw new ServiceException(OrderErrorCode.ORDER_ALREADY_CANCELED);
        }
        cancel(OrderCancelReason.BUYER_CANCELED, now);
        return true;
    }

    public boolean cancelByDeadline(LocalDateTime now) {
        if (status != OrderStatus.PAYMENT_PENDING || now.isBefore(paymentDeadlineAt)) {
            return false;
        }
        cancel(OrderCancelReason.PAYMENT_EXPIRED, now);
        return true;
    }

    public boolean cancelByOutOfStock(LocalDateTime now) {
        validatePaymentPending();
        cancel(OrderCancelReason.OUT_OF_STOCK, now);
        return true;
    }

    // 판매글 판매 중지: 결제 대기 주문만 전체 취소. 이미 결제·취소된 주문은 그대로.
    public boolean cancelBySalesStopped(LocalDateTime now) {
        if (status != OrderStatus.PAYMENT_PENDING) {
            return false;
        }
        cancel(OrderCancelReason.SALES_STOPPED, now);
        return true;
    }

    // 판매글 판매 중지: 결제 완료 주문에서 한 판매자 항목만 취소하고 취소 금액을 돌려준다 (0이면 이미 처리됨).
    // 남은 항목이 없으면 주문도 취소 상태가 된다.
    public long cancelSellerItemsBySalesStopped(Long sellerId, LocalDateTime now) {
        if (status != OrderStatus.PAID) {
            return 0;
        }
        long amount = 0;
        for (OrderItem item : items) {
            if (item.getSellerId().equals(sellerId) && !item.isCanceled()) {
                item.cancel(now);
                amount = item.amount();
            }
        }
        if (amount > 0 && items.stream().allMatch(OrderItem::isCanceled)) {
            cancel(OrderCancelReason.SALES_STOPPED, now);
        }
        return amount;
    }

    public boolean isBuyer(Long memberId) {
        return buyerMemberId.equals(memberId);
    }

    public boolean isIndividualGroup() {
        return orderType == OrderType.INDIVIDUAL_GROUP;
    }

    private void cancel(OrderCancelReason reason, LocalDateTime now) {
        this.status = OrderStatus.CANCELED;
        this.cancelReason = reason;
        this.canceledAt = now;
    }

    private void validatePaymentPending() {
        if (status != OrderStatus.PAYMENT_PENDING) {
            throw new ServiceException(OrderErrorCode.ORDER_NOT_PAYMENT_PENDING);
        }
    }

    public record OrderLine(
            Long productId,
            Long inventoryId,
            Long sellerId,
            String productName,
            String optionName,
            long unitPrice,
            int quantity
    ) {
    }
}
