package com.cartmate.cartmate.boundedContext.wallet.domain;

import com.cartmate.cartmate.boundedContext.wallet.exception.WalletErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

// 정산이 홀딩에서 꺼낸 금액의 기록. 한 정산이 여러 홀딩에서 나눠 꺼내면 홀딩마다 한 행이 생긴다.
@Entity
@Table(
        name = "holding_settlement",
        uniqueConstraints = @UniqueConstraint(name = "uk_holding_settlement_holding_payout",
                columnNames = {"holding_id", "payout_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class HoldingSettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "holding_id", nullable = false)
    private Holding holding;

    @Column(name = "payout_id", nullable = false)
    private Long payoutId;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 정산(payoutId)이 홀딩에서 amount만큼 꺼냈다는 기록을 만든다.
    // 홀딩이 없으면 WALLET_HOLDING_NOT_FOUND, 정산 ID가 없으면 WALLET_PAYOUT_ID_REQUIRED,
    // 금액이 없거나 0 이하이면 WALLET_INVALID_AMOUNT 예외가 발생한다.
    public static HoldingSettlement create(Holding holding, Long payoutId, Long amount) {
        if (holding == null) {
            throw new ServiceException(WalletErrorCode.WALLET_HOLDING_NOT_FOUND);
        }

        if (payoutId == null) {
            throw new ServiceException(WalletErrorCode.WALLET_PAYOUT_ID_REQUIRED);
        }

        if (amount == null || amount <= 0) {
            throw new ServiceException(WalletErrorCode.WALLET_INVALID_AMOUNT);
        }

        return new HoldingSettlement(holding, payoutId, amount);
    }

    private HoldingSettlement(Holding holding, Long payoutId, Long amount) {
        this.holding = holding;
        this.payoutId = payoutId;
        this.amount = amount;
    }
}
