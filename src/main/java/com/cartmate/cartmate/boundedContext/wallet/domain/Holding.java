package com.cartmate.cartmate.boundedContext.wallet.domain;

import com.cartmate.cartmate.boundedContext.wallet.exception.WalletErrorCode;
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
        name = "holding",
        uniqueConstraints = @UniqueConstraint(name = "uk_holding_order_payment",
        columnNames = {"order_id", "payment_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Holding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(nullable = false)
    private Long amount;

    @Column(name = "refunded_amount", nullable = false)
    private long refundedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HoldingStatus status;

    @Version
    private Long version;

    @Column(name = "settle_at")
    private LocalDateTime settledAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static Holding create(Wallet wallet, Long orderId, Long amount, Long paymentId) {
        if (wallet == null) {
            throw new ServiceException(WalletErrorCode.WALLET_INTERNAL_WALLET_MISSING);
        }

        if (orderId == null) {
            throw new ServiceException(WalletErrorCode.WALLET_ORDER_ID_REQUIRED);
        }

        if (amount == null || amount <= 0) {
            throw new ServiceException(WalletErrorCode.WALLET_INVALID_AMOUNT);
        }

        if (paymentId == null) {
            throw new ServiceException(WalletErrorCode.WALLET_PAYMENT_ID_REQUIRED);
        }

        return new Holding(wallet, orderId, amount, paymentId);
    }
    public void cancel() {
        if (status == HoldingStatus.CANCELED) {
            throw new ServiceException(WalletErrorCode.WALLET_HOLDING_ALREADY_CANCELED);
        }

        if (status != HoldingStatus.RESERVED) {
            throw new ServiceException(WalletErrorCode.WALLET_HOLDING_NOT_CANCELABLE);
        }

        this.status = HoldingStatus.CANCELED;
    }

    // 홀딩에 남아 있는 금액(홀딩 금액 - 환불 누적액 - 정산으로 꺼낸 금액)을 계산한다.
    // 정산으로 꺼낸 합계는 HoldingSettlement 테이블에 있어 엔티티가 알 수 없으므로 호출하는 쪽에서 넘긴다.
    public long remainingAmount(long settledAmount) {
        return amount - refundedAmount - settledAmount;
    }

    // 환불 금액을 누적하고, 남은 금액이 없어지면 홀딩을 닫는다. 정산으로 꺼낸 금액이 있으면 정산 완료, 없으면 취소 상태가 된다.
    // 환불 금액이 0 이하이면 WALLET_INVALID_AMOUNT, 홀딩이 이미 닫혔으면 WALLET_HOLDING_NOT_CANCELABLE,
    // 남은 금액을 넘으면 WALLET_HOLDING_REFUND_EXCEEDED 예외가 발생한다.
    public void addRefund(Long refundAmount, long settledAmount) {
        if (refundAmount == null || refundAmount <= 0) {
            throw new ServiceException(WalletErrorCode.WALLET_INVALID_AMOUNT);
        }

        if (status != HoldingStatus.RESERVED) {
            throw new ServiceException(WalletErrorCode.WALLET_HOLDING_NOT_CANCELABLE);
        }

        if (refundAmount > remainingAmount(settledAmount)) {
            throw new ServiceException(WalletErrorCode.WALLET_HOLDING_REFUND_EXCEEDED);
        }

        this.refundedAmount += refundAmount;

        if (remainingAmount(settledAmount) == 0) {
            close(settledAmount);
        }
    }

    // 정산으로 금액을 꺼낸 뒤 호출한다. 남은 금액이 없으면 홀딩을 정산 완료 상태로 바꾸고, 남아 있으면 그대로 둔다.
    // settledAmount는 방금 꺼낸 금액까지 포함한 정산 누적 합계다.
    public void closeIfExhausted(long settledAmount) {
        if (status == HoldingStatus.RESERVED && remainingAmount(settledAmount) == 0) {
            close(settledAmount);
        }
    }

    // 홀딩을 닫는다. 정산으로 꺼낸 금액이 있으면 정산 완료(SETTLED)와 완료 시각을 기록하고, 없으면 취소(CANCELED)로 바꾼다.
    private void close(long settledAmount) {
        if (settledAmount > 0) {
            this.status = HoldingStatus.SETTLED;
            this.settledAt = LocalDateTime.now();
            return;
        }

        this.status = HoldingStatus.CANCELED;
    }

    private Holding(Wallet wallet, Long orderId, Long amount, Long paymentId) {
        this.wallet = wallet;
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.amount = amount;
        this.status = HoldingStatus.RESERVED;
    }
}
