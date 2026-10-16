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
@Table(name = "wallet")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, unique = true)
    private Long memberId;

    @Column(name = "balance", nullable = false)
    private Long balance =0L;

    @Version
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static Wallet createWallet(Long memberId) {
        if (memberId == null) {
            throw new ServiceException(WalletErrorCode.WALLET_MEMBER_ID_REQUIRED);
        }

        return new Wallet(memberId);
    }

    public void charge(Long amount) {
        if (amount == null || amount <= 0) {
            throw new ServiceException(WalletErrorCode.WALLET_INVALID_AMOUNT);
        }

        this.balance += amount;
    }

    public void use(Long amount) {
        if (amount == null || amount <= 0) {
            throw new ServiceException(WalletErrorCode.WALLET_INVALID_AMOUNT);
        }

        if (this.balance < amount) {
            throw new ServiceException(WalletErrorCode.WALLET_INSUFFICIENT_BALANCE);
        }

        this.balance -= amount;
    }

    public void refund(Long amount) {
        if (amount == null || amount <= 0) {
            throw new ServiceException(WalletErrorCode.WALLET_INVALID_AMOUNT);
        }

        this.balance += amount;
    }

    private Wallet(Long memberId) {
        this.memberId = memberId;
        this.balance = 0L;
    }

    public void withdraw(Long amount) {
        if (amount == null || amount <= 0) {
            throw new ServiceException(WalletErrorCode.WALLET_INVALID_AMOUNT);
        }

        if (this.balance < amount) {
            throw new ServiceException(WalletErrorCode.WALLET_INSUFFICIENT_BALANCE);
        }

        this.balance -= amount;
    }
}
