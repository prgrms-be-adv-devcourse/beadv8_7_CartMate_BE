package com.cartmate.cartmate.boundedContext.orderService.order.domain.personal;

import java.util.Map;
import java.util.LinkedHashMap;
import com.cartmate.cartmate.shared.coupon.dto.CouponOrderTarget;
import com.cartmate.cartmate.shared.order.OrderCancelReason;
import com.cartmate.cartmate.shared.order.OrderStatus;
import com.cartmate.cartmate.shared.order.OrderType;
import com.cartmate.cartmate.boundedContext.orderService.order.domain.DeliveryAddress;
import com.cartmate.cartmate.boundedContext.orderService.order.exception.OrderErrorCode;
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
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_orders_order_number", columnNames = "order_number"),
                @UniqueConstraint(name = Order.PENDING_BUYER_INDIVIDUAL_GROUP_UNIQUE,
                        columnNames = {"buyer_member_id", "pending_individual_group_purchase_id"})
        },
        indexes = {
                @Index(name = "idx_orders_individual_group_purchase_status", columnList = "individual_group_purchase_id, status"),
                @Index(name = "idx_orders_buyer_created", columnList = "buyer_member_id, created_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    public static final Duration PAYMENT_PERIOD = Duration.ofMinutes(30);
    public static final String PENDING_BUYER_INDIVIDUAL_GROUP_UNIQUE = "uk_orders_pending_buyer_individual_group";

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

    @Column(name = "pending_individual_group_purchase_id")
    private Long pendingIndividualGroupPurchaseId;

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

    // ===== 쿠폰 할인 (결제할 때 정해짐) =====
    // 쓴 쿠폰 (회원 쿠폰 ID). 없으면 null
    @Column(name = "member_coupon_id")
    private Long memberCouponId;

    // 할인액. 결제 금액 = totalAmount - discountAmount
    @Column(name = "discount_amount", nullable = false)
    private long discountAmount;

    @Column(name = "discount_burden", columnDefinition = "varchar(20)")
    @Enumerated(EnumType.STRING)
    private OrderDiscountBurden discountBurden;

    // 판매자 쿠폰이면 그 판매자 (할인 대상 = 그 판매자 상품)
    @Column(name = "discount_seller_id")
    private Long discountSellerId;

    // 판매자 상품 일부 취소로 지금까지 환불한 금액 (마지막 취소에서 남은 결제 금액을 정확히 돌려주려고)
    @Column(name = "item_refunded_amount", nullable = false)
    private long itemRefundedAmount;

    // 일부 상품만 취소된 적이 있는지 (있으면 나중에 전부 취소돼도 쿠폰을 돌려주지 않는다)
    @Column(name = "partially_canceled", nullable = false)
    private boolean partiallyCanceled;

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
        this.pendingIndividualGroupPurchaseId = orderType == OrderType.INDIVIDUAL_GROUP ? individualGroupPurchaseId : null;
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
        releasePending();
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

    public boolean cancelBySalesStopped(LocalDateTime now) {
        if (status != OrderStatus.PAYMENT_PENDING) {
            return false;
        }
        cancel(OrderCancelReason.SALES_STOPPED, now);
        return true;
    }

    public boolean cancelByGroupPurchaseClosed(LocalDateTime now) {
        if (status != OrderStatus.PAYMENT_PENDING) {
            return false;
        }
        cancel(OrderCancelReason.GROUP_PURCHASE_CLOSED, now);
        return true;
    }


    public void applyCoupon(Long memberCouponId, long discountAmount, boolean platformBurden, Long sellerId) {
        if (this.memberCouponId != null) {
            if (this.memberCouponId.equals(memberCouponId) && this.discountAmount == discountAmount) {
                return;
            }
            throw new ServiceException(OrderErrorCode.ORDER_COUPON_ALREADY_APPLIED);
        }
        validatePaymentPending();
        if (discountAmount < 0 || discountAmount > totalAmount) {
            throw new ServiceException(OrderErrorCode.ORDER_INVALID_DISCOUNT);
        }
        this.memberCouponId = memberCouponId;
        this.discountAmount = discountAmount;
        this.discountBurden = platformBurden ? OrderDiscountBurden.PLATFORM : OrderDiscountBurden.SELLER;
        this.discountSellerId = platformBurden ? null : sellerId;
    }

    public long payAmount() {
        return totalAmount - discountAmount;
    }

    public boolean hasCoupon() {
        return memberCouponId != null;
    }

    public CouponOrderTarget couponTarget() {
        Map<Long, Long> bySeller = new LinkedHashMap<>();
        for (OrderItem item : items) {
            if (item.getSellerId() != null) {
                bySeller.merge(item.getSellerId(), item.amount(), Long::sum);
            }
        }
        return new CouponOrderTarget(totalAmount, bySeller.entrySet().stream()
                .map(e -> new CouponOrderTarget.SellerAmount(e.getKey(), e.getValue()))
                .toList());
    }

    public boolean couponRestorable() {
        return hasCoupon() && status == OrderStatus.CANCELED && !partiallyCanceled;
    }

    private long discountShareOf(Long sellerId, long itemsAmount) {
        if (discountAmount == 0 || itemsAmount == 0) {
            return 0;
        }
        if (discountBurden == OrderDiscountBurden.SELLER) {
            if (!sellerId.equals(discountSellerId)) {
                return 0;
            }
            long sellerTotal = items.stream().filter(i -> sellerId.equals(i.getSellerId())).mapToLong(OrderItem::amount).sum();
            return discountAmount * itemsAmount / sellerTotal;
        }
        return discountAmount * itemsAmount / totalAmount;
    }

    public long cancelSellerItemsBySalesStopped(Long sellerId, LocalDateTime now) {
        if (status != OrderStatus.PAID) {
            return 0;
        }
        long itemsAmount = 0;
        for (OrderItem item : items) {
            if (sellerId.equals(item.getSellerId()) && !item.isCanceled()) {
                item.cancel(now);
                itemsAmount += item.amount();
            }
        }
        if (itemsAmount == 0) {
            return 0;
        }
        // 환불 = 상품 금액 - 그 상품에 붙은 쿠폰 할인 몫. 이걸로 모든 상품이 취소되면 남은 결제 금액을 정확히 돌려준다 (나눗셈 끝전 포함)
        long refund = itemsAmount - discountShareOf(sellerId, itemsAmount);
        if (items.stream().allMatch(OrderItem::isCanceled)) {
            refund = payAmount() - itemRefundedAmount;
            cancel(OrderCancelReason.SALES_STOPPED, now);
        } else {
            partiallyCanceled = true;
        }
        itemRefundedAmount += refund;
        return refund;
    }

    public boolean isBuyer(Long memberId) {
        return buyerMemberId.equals(memberId);
    }

    public boolean isIndividualGroup() {
        return orderType == OrderType.INDIVIDUAL_GROUP;
    }

    public boolean isPaymentPending() {
        return status == OrderStatus.PAYMENT_PENDING;
    }

    public List<Long> productIds() {
        return items.stream().map(OrderItem::getProductId).distinct().toList();
    }

    private void cancel(OrderCancelReason reason, LocalDateTime now) {
        this.status = OrderStatus.CANCELED;
        this.cancelReason = reason;
        this.canceledAt = now;
        releasePending();
    }

    private void releasePending() {
        this.pendingIndividualGroupPurchaseId = null;
        items.forEach(OrderItem::releasePending);
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
