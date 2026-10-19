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
import java.time.LocalTime;

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

    // 수수료율 3%, 구매확정 월의 다음 달 15일에 지급한다.
    private static final long FEE_RATE_PERCENT = 3L;
    private static final int PAYOUT_DAY_OF_MONTH = 15;

    public static Payout create(Long sellerId, Long orderId, Long orderItemId, Long salesAmount, LocalDateTime confirmedAt) {
        if (sellerId == null || orderId == null || orderItemId == null || confirmedAt == null) {
            throw new ServiceException(PayoutErrorCode.PAYOUT_REQUIRED_VALUE_MISSING);
        }

        if (salesAmount == null || salesAmount < 0) {
            throw new ServiceException(PayoutErrorCode.PAYOUT_INVALID_AMOUNT);
        }

        return new Payout(sellerId, orderId, orderItemId, salesAmount, confirmedAt);
    }

    // 구매확정 시각이 속한 달의 다음 달 15일 00:00을 지급 예정 시각으로 계산한다.
    private static LocalDateTime scheduledAtFor(LocalDateTime confirmedAt) {
        return confirmedAt.toLocalDate()
                .plusMonths(1)
                .withDayOfMonth(PAYOUT_DAY_OF_MONTH)
                .atTime(LocalTime.MIN);
    }

    private Payout(Long sellerId, Long orderId, Long orderItemId, Long salesAmount, LocalDateTime confirmedAt) {
        this.sellerId = sellerId;
        this.orderId = orderId;
        this.orderItemId = orderItemId;
        this.salesAmount = salesAmount;
        this.feeAmount = salesAmount * FEE_RATE_PERCENT / 100;
        this.payoutAmount = salesAmount - this.feeAmount;
        this.confirmedAt = confirmedAt;
        this.scheduledAt = scheduledAtFor(confirmedAt);
        this.status = PayoutStatus.PENDING;
    }
}
