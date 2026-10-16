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

    @Column(name = "settlement_id")
    private Long settlementId;

    @Column(nullable = false)
    private Long amount;

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

    public void settle(Long settlementId) {
        if (settlementId == null) {
            throw new ServiceException(WalletErrorCode.WALLET_SETTLEMENT_ID_REQUIRED);
        }

        if (status == HoldingStatus.SETTLED) {
            throw new ServiceException(WalletErrorCode.WALLET_HOLDING_ALREADY_SETTLED);
        }

        if (status != HoldingStatus.RESERVED) {
            throw new ServiceException(WalletErrorCode.WALLET_HOLDING_NOT_SETTLEABLE);
        }

        this.status = HoldingStatus.SETTLED;
        this.settlementId = settlementId;
        this.settledAt = LocalDateTime.now();
    }

    private Holding(Wallet wallet, Long orderId, Long amount, Long paymentId) {
        this.wallet = wallet;
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.amount = amount;
        this.status = HoldingStatus.RESERVED;
    }
}
