package com.cartmate.cartmate.boundedContext.payout.domain;

import com.cartmate.cartmate.boundedContext.payout.exception.PayoutErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "payout",
        uniqueConstraints = @UniqueConstraint(name = "uk_payout_order_item", columnNames = {"order_item_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Payout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "order_item_id", nullable = false)
    private Long orderItemId;

    // 주문 시점의 상품명 스냅샷. 이 컬럼이 생기기 전에 만든 정산은 null이다.
    @Column(name = "product_name")
    private String productName;

    // 정산 대상 수량. 구매확정 전에 환불된 수량은 뺀 값이다. 이 컬럼이 생기기 전에 만든 정산은 null이다.
    @Column(name = "quantity")
    private Long quantity;

    // 주문 시각. 이 컬럼이 생기기 전에 만든 정산은 null이다.
    @Column(name = "ordered_at")
    private LocalDateTime orderedAt;

    @Column(name = "sales_amount", nullable = false)
    private Long salesAmount;

    @Column(name = "fee_amount", nullable = false)
    private Long feeAmount;

    @Column(name = "payout_amount", nullable = false)
    private Long payoutAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PayoutStatus status;

    @Column(name = "confirmed_at", nullable = false)
    private LocalDateTime confirmedAt;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;


    public static Payout create(Long sellerId, Long orderId, Long orderItemId, String productName, Long quantity,
                                Long salesAmount, LocalDateTime orderedAt, LocalDateTime confirmedAt) {
        if (sellerId == null || orderId == null || orderItemId == null || confirmedAt == null) {
            throw new ServiceException(PayoutErrorCode.PAYOUT_REQUIRED_VALUE_MISSING);
        }

        if (salesAmount == null || salesAmount < 0) {
            throw new ServiceException(PayoutErrorCode.PAYOUT_INVALID_AMOUNT);
        }

        return new Payout(sellerId, orderId, orderItemId, productName, quantity, salesAmount, orderedAt, confirmedAt);
    }

    // 대기 중인 정산을 지급 완료로 바꾸고 지급 시각을 기록한다.
    public void complete(LocalDateTime now) {
        if (status != PayoutStatus.PENDING) {
            throw new ServiceException(PayoutErrorCode.PAYOUT_NOT_PENDING);
        }

        this.status = PayoutStatus.COMPLETED;
        this.paidAt = now;
    }

    private Payout(Long sellerId, Long orderId, Long orderItemId, String productName, Long quantity,
                   Long salesAmount, LocalDateTime orderedAt, LocalDateTime confirmedAt) {
        this.sellerId = sellerId;
        this.orderId = orderId;
        this.orderItemId = orderItemId;
        this.productName = productName;
        this.quantity = quantity;
        this.orderedAt = orderedAt;
        this.salesAmount = salesAmount;
        this.feeAmount = PayoutPolicy.feeOf(salesAmount);
        this.payoutAmount = salesAmount - this.feeAmount;
        this.confirmedAt = confirmedAt;
        this.scheduledAt = PayoutPolicy.scheduledAtFor(confirmedAt);
        this.status = PayoutStatus.PENDING;
    }
}
