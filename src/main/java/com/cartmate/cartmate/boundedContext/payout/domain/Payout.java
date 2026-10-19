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


    public static Payout create(Long sellerId, Long orderId, Long orderItemId, Long salesAmount, LocalDateTime confirmedAt) {
        if (sellerId == null || orderId == null || orderItemId == null || confirmedAt == null) {
            throw new ServiceException(PayoutErrorCode.PAYOUT_REQUIRED_VALUE_MISSING);
        }

        if (salesAmount == null || salesAmount < 0) {
            throw new ServiceException(PayoutErrorCode.PAYOUT_INVALID_AMOUNT);
        }

        return new Payout(sellerId, orderId, orderItemId, salesAmount, confirmedAt);
    }

    // 대기 중인 정산을 지급 완료로 바꾸고 지급 시각을 기록한다.
    public void complete(LocalDateTime now) {
        if (status != PayoutStatus.PENDING) {
            throw new ServiceException(PayoutErrorCode.PAYOUT_NOT_PENDING);
        }

        this.status = PayoutStatus.COMPLETED;
        this.paidAt = now;
    }

    private Payout(Long sellerId, Long orderId, Long orderItemId, Long salesAmount, LocalDateTime confirmedAt) {
        this.sellerId = sellerId;
        this.orderId = orderId;
        this.orderItemId = orderItemId;
        this.salesAmount = salesAmount;
        this.feeAmount = PayoutPolicy.feeOf(salesAmount);
        this.payoutAmount = salesAmount - this.feeAmount;
        this.confirmedAt = confirmedAt;
        this.scheduledAt = PayoutPolicy.scheduledAtFor(confirmedAt);
        this.status = PayoutStatus.PENDING;
    }
}
