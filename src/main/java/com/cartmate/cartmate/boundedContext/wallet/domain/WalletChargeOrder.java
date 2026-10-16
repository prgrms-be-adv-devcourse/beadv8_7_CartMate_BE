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
import java.util.UUID;

@Entity
@Table(name = "wallet_charge_orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@EntityListeners(AuditingEntityListener.class)
public class WalletChargeOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Column(name = "order_number", nullable = false, unique = true)
    private String orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChargeStatus status;

    @Column(nullable = false)
    private Long amount;

    @Column(name = "payment_key", unique = true)
    private String paymentKey;

    @Column(name = "idempotency_Key", nullable = false, unique = true)
    private String idempotencyKey;

    @Version
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static WalletChargeOrder create(Wallet wallet, Long amount, String idempotencyKey) {
        if (wallet == null) {
            throw new ServiceException(WalletErrorCode.WALLET_INTERNAL_WALLET_MISSING);
        }

        if (amount == null || amount <= 0) {
            throw new ServiceException(WalletErrorCode.WALLET_INVALID_AMOUNT);
        }

        return new WalletChargeOrder(wallet, amount, idempotencyKey);

    }

    public void complete(String paymentKey) {
        if (status != ChargeStatus.PENDING) {
            throw new ServiceException(WalletErrorCode.WALLET_CHARGE_NOT_COMPLETABLE);
        }

        if (paymentKey == null || paymentKey.isBlank()) {
            throw new ServiceException(WalletErrorCode.WALLET_PAYMENT_KEY_REQUIRED);
        }

        this.status = ChargeStatus.COMPLETED;
        this.paymentKey = paymentKey;
    }

    public void fail() {
        if (status != ChargeStatus.PENDING) {
            throw new ServiceException(WalletErrorCode.WALLET_CHARGE_NOT_FAILABLE);
        }

        this.status = ChargeStatus.FAILED;
    }

    public void cancel() {
        if (status != ChargeStatus.PENDING) {
            throw new ServiceException(WalletErrorCode.WALLET_CHARGE_NOT_CANCELABLE);
        }

        this.status = ChargeStatus.CANCELED;
    }

    private WalletChargeOrder(Wallet wallet, Long amount, String idempotencyKey) {
        this.wallet = wallet;
        this.amount = amount;
        this.orderNumber = "Charge_" + UUID.randomUUID();
        this.status = ChargeStatus.PENDING;
        this.idempotencyKey = idempotencyKey;
    }
}
