package com.cartmate.cartmate.boundedContext.orderService.order.domain.personal;

import com.cartmate.cartmate.shared.cart.PaybackFunder;
import com.cartmate.cartmate.shared.order.GroupPurchaseType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "payback_payout",
        uniqueConstraints = @UniqueConstraint(name = "uk_payback_payout_purchase_member_product_funder",
                columnNames = {"group_purchase_type", "group_purchase_id", "member_id", "product_id", "funder"}),
        indexes = @Index(name = "idx_payback_payout_status", columnList = "status, id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaybackPayout {

    public static final int MAX_AUTO_ATTEMPTS = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "group_purchase_type", nullable = false, length = 20)
    private GroupPurchaseType groupPurchaseType;

    @Column(name = "group_purchase_id", nullable = false)
    private Long groupPurchaseId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaybackFunder funder;

    private Long sellerId;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private long amountPerUnit;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaybackPayoutStatus status;

    @Column(nullable = false)
    private int attempt;

    @Column(length = 100)
    private String failureCode;

    @Column(nullable = false)
    private LocalDateTime requestedAt;

    private LocalDateTime completedAt;

    private PaybackPayout(GroupPurchaseType groupPurchaseType, Long groupPurchaseId,
                          PaybackSettlement.PayoutLine line, LocalDateTime requestedAt) {
        this.groupPurchaseType = groupPurchaseType;
        this.groupPurchaseId = groupPurchaseId;
        this.memberId = line.memberId();
        this.productId = line.productId();
        this.funder = line.funder();
        this.sellerId = line.sellerId();
        this.quantity = line.quantity();
        this.amountPerUnit = line.amountPerUnit();
        this.amount = line.amount();
        this.status = PaybackPayoutStatus.REQUESTED;
        this.attempt = 1;
        this.requestedAt = requestedAt;
    }

    public static PaybackPayout request(GroupPurchaseType groupPurchaseType, Long groupPurchaseId,
                                        PaybackSettlement.PayoutLine line, LocalDateTime requestedAt) {
        if (line.quantity() <= 0 || line.amountPerUnit() <= 0) {
            throw new IllegalArgumentException("payback amount must be positive");
        }
        return new PaybackPayout(groupPurchaseType, groupPurchaseId, line, requestedAt);
    }

    public boolean markPaid(LocalDateTime depositedAt) {
        if (status == PaybackPayoutStatus.PAID) {
            return false;
        }
        status = PaybackPayoutStatus.PAID;
        failureCode = null;
        completedAt = depositedAt;
        return true;
    }

    public boolean markFailed(String failureCode, boolean retryable, int failedAttempt, LocalDateTime failedAt) {
        if (status != PaybackPayoutStatus.REQUESTED || failedAttempt != attempt) {
            return false;
        }
        this.failureCode = failureCode;
        if (retryable && attempt < MAX_AUTO_ATTEMPTS) {
            status = PaybackPayoutStatus.RETRY_WAITING;
        } else {
            status = PaybackPayoutStatus.FAILED;
            completedAt = failedAt;
        }
        return true;
    }

    public boolean matches(Long memberId, long amount) {
        return this.memberId.equals(memberId) && this.amount == amount;
    }

    public boolean isRetryWaiting() {
        return status == PaybackPayoutStatus.RETRY_WAITING;
    }

    public boolean isFailed() {
        return status == PaybackPayoutStatus.FAILED;
    }

    public void retry(LocalDateTime requestedAt) {
        if (!isRetryWaiting() && !isFailed()) {
            throw new IllegalStateException("only waiting or failed payouts can be retried: " + status);
        }
        status = PaybackPayoutStatus.REQUESTED;
        attempt++;
        completedAt = null;
        this.requestedAt = requestedAt;
    }
}
