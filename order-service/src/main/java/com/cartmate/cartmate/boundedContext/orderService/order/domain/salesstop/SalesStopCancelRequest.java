package com.cartmate.cartmate.boundedContext.orderService.order.domain.salesstop;

import com.cartmate.cartmate.shared.delivery.PurchaseType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 판매글 내림으로 인한 취소를 배송 서버에 요청하고 결과를 기다리는 장부.
 * 한 줄이 "어느 주문의 어느 판매자 몫"이고, 같은 판매자 몫은 한 번만 요청한다.
 * 결과가 오기 전까지 주문과 항목은 바꾸지 않는다. 늦게 온 결과나 중복 결과는 줄이 요청됨이 아니면 반영하지 않는다.
 */
@Entity
@Table(name = "sales_stop_cancel_request",
        uniqueConstraints = @UniqueConstraint(name = "uk_sales_stop_cancel_request",
                columnNames = {"purchase_type", "order_id", "seller_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SalesStopCancelRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "purchase_type", nullable = false, columnDefinition = "varchar(20)")
    private PurchaseType purchaseType;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "varchar(20)")
    private SalesStopCancelRequestStatus status;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "last_sent_at", nullable = false)
    private LocalDateTime lastSentAt;

    @Column(name = "reject_code", length = 100)
    private String rejectCode;

    public boolean isRequested() {
        return status == SalesStopCancelRequestStatus.REQUESTED;
    }

    public boolean resend(LocalDateTime now, Duration resendAfter) {
        if (!isRequested() || lastSentAt.plus(resendAfter).isAfter(now)) {
            return false;
        }
        this.lastSentAt = now;
        return true;
    }

    public void complete() {
        this.status = SalesStopCancelRequestStatus.CANCELED;
    }

    public void reject(String rejectCode) {
        this.status = SalesStopCancelRequestStatus.REJECTED;
        this.rejectCode = rejectCode;
    }
}
