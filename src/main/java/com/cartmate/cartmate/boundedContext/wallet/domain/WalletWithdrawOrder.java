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
@Table(name = "wallet_withdraw_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class WalletWithdrawOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WithdrawStatus status;

    @Version
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static WalletWithdrawOrder create(Wallet wallet, String idempotencyKey, Long amount) {
        if (wallet == null) {
            throw new ServiceException(WalletErrorCode.WALLET_INTERNAL_WALLET_MISSING);
        }

        if (amount == null || amount <= 0) {
            throw new ServiceException(WalletErrorCode.WALLET_INVALID_AMOUNT);
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ServiceException(WalletErrorCode.WALLET_IDEMPOTENCY_KEY_REQUIRED);
        }

        return new WalletWithdrawOrder(wallet, idempotencyKey, amount);
    }

    public void complete() {
        if (status != WithdrawStatus.PENDING) {
            throw new ServiceException(WalletErrorCode.WALLET_WITHDRAW_NOT_COMPLETABLE);
        }

        this.status = WithdrawStatus.COMPLETED;
    }

    // 대기 중인 출금 요청을 실패 상태로 변경한다.
    public void fail() {
        if (status != WithdrawStatus.PENDING) {
            throw new ServiceException(WalletErrorCode.WALLET_WITHDRAW_NOT_FAILABLE);
        }

        this.status = WithdrawStatus.FAILED;
    }

    // 대기 중인 출금 요청을 취소 상태로 변경한다.
    public void cancel() {
        if (status != WithdrawStatus.PENDING) {
            throw new ServiceException(WalletErrorCode.WALLET_WITHDRAW_NOT_CANCELABLE);
        }

        this.status = WithdrawStatus.CANCELED;
    }

    private WalletWithdrawOrder(Wallet wallet, String idempotencyKey, Long amount) {
        this.wallet = wallet;
        this.idempotencyKey = idempotencyKey;
        this.status = WithdrawStatus.PENDING;
        this.amount = amount;
    }
}
