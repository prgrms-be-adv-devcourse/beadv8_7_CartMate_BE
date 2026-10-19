package com.cartmate.cartmate.boundedContext.payment.domain;

import com.cartmate.cartmate.boundedContext.payment.exception.PaymentErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "payment",
        uniqueConstraints = @UniqueConstraint(name = "uk_payment_order_member", columnNames = {"order_id", "member_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseIdAndTime {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "wallet_id", nullable = false)
    private Long walletId;

    @Column(nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, length = 20)
    private PaymentType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Version
    private Long version;

    public static Payment pay(Long orderId, Long memberId, Long walletId, Long amount, PaymentType type) {
        if (amount == null || amount <= 0) {
            throw new ServiceException(PaymentErrorCode.PAYMENT_INVALID_AMOUNT);
        }
        Payment payment = new Payment();
        payment.orderId = orderId;
        payment.memberId = memberId;
        payment.walletId = walletId;
        payment.amount = amount;
        payment.type = type;
        payment.status = PaymentStatus.PAID;
        payment.paidAt = LocalDateTime.now();
        return payment;
    }

    public void cancel() {
        if (status == PaymentStatus.CANCELED) {
            throw new ServiceException(PaymentErrorCode.PAYMENT_ALREADY_CANCELED);
        }
        this.status = PaymentStatus.CANCELED;
        this.canceledAt = LocalDateTime.now();
    }

    public boolean isOwnedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }
}