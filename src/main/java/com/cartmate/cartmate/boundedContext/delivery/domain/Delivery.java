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
@Table(name = "delivery")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purchase_type", nullable = false)
    private PurchaseType purchaseType;

    @Column(name = "tracking_number")
    private String trackingNumber;

    @Column(name = "receiver_name", nullable = false)
    private String receiverName;

    @Column(name = "receiver_phone", nullable = false)
    private String receiverPhone;

    @Column(name = "zip_code", nullable = false)
    private String zipCode;

    @Column(name = "address", nullable = false)
    private String address;

    @Column(name = "address_detail", nullable = false)
    private String addressDetail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DeliveryStatus status;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;    //배송출발시간

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;  //배송 완료시간

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;  //구매확정 시간

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;    //배송생성시간

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;


    public static Delivery create(
            Long memberId, Long orderId, PurchaseType purchaseType,
            String receiverName, String receiverPhone,
            String zipCode, String address, String addressDetail, String idempotencyKey
    ) {
        return new Delivery(
                memberId, orderId, purchaseType,
                receiverName, receiverPhone, zipCode, address, addressDetail, idempotencyKey
        );
    }

    private Delivery(
            Long memberId, Long orderId, PurchaseType purchaseType,
            String receiverName, String receiverPhone,
            String zipCode, String address, String addressDetail, String idempotencyKey
    ) {
        this.memberId = memberId;
        this.orderId = orderId;
        this.purchaseType = purchaseType;
        this.receiverName = receiverName;
        this.receiverPhone = receiverPhone;
        this.zipCode = zipCode;
        this.address = address;
        this.addressDetail = addressDetail;
        this.status = DeliveryStatus.PREPARING;
        this.idempotencyKey = idempotencyKey;
    }
}
