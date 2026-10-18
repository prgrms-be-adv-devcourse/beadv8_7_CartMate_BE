package com.cartmate.cartmate.boundedContext.delivery.domain;

import com.cartmate.cartmate.boundedContext.delivery.exception.DeliveryErrorCode;
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
@Table(name = "delivery_claim")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class DeliveryClaim {

    public static final int DETAIL_MAX_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_id", nullable = false)
    private Delivery delivery;

    @Column(name = "member_id", nullable = false)
    private Long memberId;      //접수한 구매자

    @Enumerated(EnumType.STRING)
    @Column(name = "claim_type", nullable = false)
    private ClaimType claimType;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false)
    private ClaimReason reason;

    @Column(name = "detail", length = DETAIL_MAX_LENGTH)
    private String detail;      //상세 사유

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ClaimStatus status;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;  //판매자 승인·거절 시각

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;    //접수 시각

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static DeliveryClaim create(
            Delivery delivery, Long memberId, ClaimType claimType, ClaimReason reason, String detail
    ) {
        return new DeliveryClaim(delivery, memberId, claimType, reason, detail);
    }

    private DeliveryClaim(
            Delivery delivery, Long memberId, ClaimType claimType, ClaimReason reason, String detail
    ) {
        this.delivery = delivery;
        this.memberId = memberId;
        this.claimType = claimType;
        this.reason = reason;
        this.detail = detail;
        this.status = ClaimStatus.REQUESTED;
    }

    // 판매자가 접수된 교환·환불을 수락한다. 이미 수락된 클레임이면 아무것도 하지 않아 재요청해도 안전하다.
    public void approve() {
        if (this.status == ClaimStatus.APPROVED) {
            return;
        }
        if (this.status != ClaimStatus.REQUESTED) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_CLAIM_INVALID_STATUS);
        }

        this.status = ClaimStatus.APPROVED;
        this.processedAt = LocalDateTime.now();
    }
}
