package com.cartmate.cartmate.boundedContext.orderService.order.domain.personal;

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

// 달성한 공동구매의 구매자 1명 = 1행. id가 wallet 적립 멱등 키(paybackId)다
@Getter
@Entity
@Table(name = "individual_group_purchase_payback_payout",
        uniqueConstraints = @UniqueConstraint(name = "uk_payback_payout_purchase_member",
                columnNames = {"individual_group_purchase_id", "member_id"}),
        indexes = @Index(name = "idx_payback_payout_status", columnList = "status, id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaybackPayout {

    public static final int MAX_AUTO_ATTEMPTS = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "individual_group_purchase_id", nullable = false)
    private Long individualGroupPurchaseId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

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

    private PaybackPayout(Long individualGroupPurchaseId, Long memberId, long amount, LocalDateTime requestedAt) {
        this.individualGroupPurchaseId = individualGroupPurchaseId;
        this.memberId = memberId;
        this.amount = amount;
        this.status = PaybackPayoutStatus.REQUESTED;
        this.attempt = 1;
        this.requestedAt = requestedAt;
    }

    public static PaybackPayout request(Long individualGroupPurchaseId, Long memberId, long amount,
                                        LocalDateTime requestedAt) {
        if (amount <= 0) {
            throw new IllegalArgumentException("payback amount must be positive");
        }
        return new PaybackPayout(individualGroupPurchaseId, memberId, amount, requestedAt);
    }

    // 이미 지급됐으면 늦게 온 결과는 무시한다(같은 paybackId로 여러 번 요청할 수 있어서)
    public boolean markPaid(LocalDateTime depositedAt) {
        if (status == PaybackPayoutStatus.PAID) {
            return false;
        }
        status = PaybackPayoutStatus.PAID;
        failureCode = null;
        completedAt = depositedAt;
        return true;
    }

    // 재시도할 수 있는 실패는 자동 재요청 횟수가 남았을 때만 대기로, 아니면 실패로 확정한다.
    // 앞선 시도의 늦은 실패는 지금 요청의 결과가 아니므로 무시한다(성공은 시도와 상관없이 적립된 것이라 받는다)
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

    // 자동 재요청(RETRY_WAITING)과 어드민 재요청(FAILED) 모두 같은 paybackId로 다시 보낸다
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
