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
@Table(name = "holding")
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
            throw new IllegalArgumentException("지갑은 필수입니다.");
        }

        if (orderId == null) {
            throw new IllegalArgumentException("주문ID는 필수입니다.");
        }

        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("홀딩금액은 0이상이어야 합니다.");
        }

        if (paymentId == null) {
            throw new IllegalArgumentException("결제ID는 필수입니다.");
        }

        return new Holding(wallet, orderId, amount, paymentId);
    }
    public void cancel() {
        if (status == HoldingStatus.CANCELED) {
            throw new IllegalArgumentException("이미 취소된 홀딩입니다.");
        }

        if (status != HoldingStatus.RESERVED) {
            throw new IllegalArgumentException("취소할수 없는 홀딩입니다.");
        }

        this.status = HoldingStatus.CANCELED;
    }

    public void settle(Long settlementId) {
        if (settlementId == null) {
            throw new IllegalArgumentException("정산ID는 필수입니다.");
        }

        if (status == HoldingStatus.SETTLED) {
            throw new IllegalArgumentException("이미 정산된 홀딩입니다.");
        }

        if (status != HoldingStatus.RESERVED) {
            throw new IllegalArgumentException("정산할 수 없는 홀딩입니다.");
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
