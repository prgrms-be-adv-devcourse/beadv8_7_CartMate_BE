package com.cartmate.cartmate.boundedContext.wallet.domain;

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
        name = "wallet_transaction",
        uniqueConstraints = @UniqueConstraint(name = "uk_wallet_transaction_operation",
        columnNames = {"type", "order_id", "reference_id", "reference_type"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private WalletTransactionType type;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "reference_id")
    private Long referenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type")
    private ReferenceType referenceType;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "balance_after", nullable = false)
    private Long balanceAfter;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static WalletTransaction create(
            Wallet wallet,
            WalletTransactionType type,
            Long orderId,
            Long amount,
            Long balanceAfter,
            Long referenceId,
            ReferenceType referenceType

    ) {
        if (wallet == null || type == null) {
            throw new IllegalArgumentException("지갑과 거래타입은 필수입니다.");
        }

        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("거래금액은 0이상이어야 합니다.");
        }

        if (balanceAfter == null || balanceAfter < 0) {
            throw new IllegalArgumentException("거래 후 잔액은 0이상이어야합니다.");
        }

        WalletTransaction walletTransaction = new WalletTransaction();

        walletTransaction.wallet = wallet;
        walletTransaction.type = type;
        walletTransaction.orderId = orderId;
        walletTransaction.amount = amount;
        walletTransaction.balanceAfter = balanceAfter;
        walletTransaction.referenceId = referenceId;
        walletTransaction.referenceType = referenceType;

        return walletTransaction;
    }
}
