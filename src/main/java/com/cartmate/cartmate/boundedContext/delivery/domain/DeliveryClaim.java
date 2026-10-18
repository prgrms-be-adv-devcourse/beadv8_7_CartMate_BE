package com.cartmate.cartmate.boundedContext.delivery.domain;

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

    @Column(name = "detail", length = 1000)
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
}
