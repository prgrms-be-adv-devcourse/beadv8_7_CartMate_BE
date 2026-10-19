package com.cartmate.cartmate.boundedContext.payment.domain;

import com.cartmate.cartmate.boundedContext.payment.exception.PaymentErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 결제 금액 중 판매자 한 명의 몫과 환불된 금액
@Entity
@Table(
        name = "payment_seller_amount",
        uniqueConstraints = @UniqueConstraint(name = "uk_payment_seller", columnNames = {"payment_id", "seller_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentSellerAmount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(nullable = false)
    private Long amount;

    @Column(name = "refunded_amount", nullable = false)
    private Long refundedAmount;

    static PaymentSellerAmount of(Payment payment, Long sellerId, Long amount) {
        PaymentSellerAmount s = new PaymentSellerAmount();
        s.payment = payment;
        s.sellerId = sellerId;
        s.amount = amount;
        s.refundedAmount = 0L;
        return s;
    }

    public long refundableAmount() {
        return amount - refundedAmount;
    }

    void refund(Long refundAmount) {
        if (refundAmount > refundableAmount()) {
            throw new ServiceException(PaymentErrorCode.PAYMENT_REFUND_EXCEEDS_REMAINING);
        }
        this.refundedAmount += refundAmount;
    }
}