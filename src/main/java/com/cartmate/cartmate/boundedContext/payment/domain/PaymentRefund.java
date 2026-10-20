package com.cartmate.cartmate.boundedContext.payment.domain;

import com.cartmate.cartmate.global.jpa.entity.BaseIdAndTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// 환불 한 건의 기록. 같은 원인으로 두 번 환불되지 않도록 refundKey를 UNIQUE로 둔다
@Entity
@Table(
        name = "payment_refund",
        uniqueConstraints = @UniqueConstraint(name = "uk_payment_refund_key", columnNames = "refund_key")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentRefund extends BaseIdAndTime {

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    // 예: ORDER-CANCEL-3, SHARED-CANCEL-7, ADMIN-3-1700000000000
    @Column(name = "refund_key", nullable = false, length = 100)
    private String refundKey;

    @Column(nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private PaymentRefundReason reason;

    @Column(length = 300)
    private String memo;

    public static PaymentRefund create(Long paymentId, String refundKey, Long amount,
                                       PaymentRefundReason reason, String memo) {
        PaymentRefund r = new PaymentRefund();
        r.paymentId = paymentId;
        r.refundKey = refundKey;
        r.amount = amount;
        r.reason = reason;
        r.memo = memo;
        return r;
    }
}