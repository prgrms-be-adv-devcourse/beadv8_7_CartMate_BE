package com.cartmate.cartmate.boundedContext.payment.domain;

import com.cartmate.cartmate.boundedContext.payment.exception.PaymentErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import com.cartmate.cartmate.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

// 개인 주문(orders)과 공동 주문(shared_group_order)은 번호가 겹칠 수 있어서 결제 종류까지 묶어 UNIQUE
@Entity
@Table(
        name = "payment",
        uniqueConstraints = @UniqueConstraint(name = "uk_payment_type_order_member",
                columnNames = {"payment_type", "order_id", "member_id"})
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
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "payment_type", nullable = false, length = 20)
    private PaymentType type;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
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

    public boolean isOwnedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }

    public boolean isGroupShare() {
        return type == PaymentType.GROUP_SHARE;
    }
}