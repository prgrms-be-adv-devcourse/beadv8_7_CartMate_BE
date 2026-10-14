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

    @Column(nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HoldingStatus status;

    @Version
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static Holding create(Wallet wallet, Long orderId, Long amount) {
        if (wallet == null) {
            throw new IllegalArgumentException("지갑은 필수입니다.");
        }

        if (orderId == null) {
            throw new IllegalArgumentException("주문ID는 필수입니다.");
        }

        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("홀딩금액은 0이상이어야 합니다.");
        }

        return new Holding(wallet, orderId, amount);
    }

    private Holding(Wallet wallet, Long orderId, Long amount) {
        this.wallet = wallet;
        this.orderId = orderId;
        this.amount = amount;
        this.status = HoldingStatus.RESERVED;
    }
}
