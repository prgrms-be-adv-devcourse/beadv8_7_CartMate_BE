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

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purchase_type", nullable = false)
    private PurchaseType purchaseType;

    @Column(name = "carrier")
    private String carrier;

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

    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;     //요청 내용 SHA-256 해시 (멱등키 재요청 비교용)

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
            Long memberId, Long sellerId, Long orderId, PurchaseType purchaseType,
            String receiverName, String receiverPhone,
            String zipCode, String address, String addressDetail, String idempotencyKey,
            String requestHash
    ) {
        return new Delivery(
                memberId, sellerId, orderId, purchaseType,
                receiverName, receiverPhone, zipCode, address, addressDetail, idempotencyKey, requestHash
        );
    }

    private Delivery(
            Long memberId, Long sellerId, Long orderId, PurchaseType purchaseType,
            String receiverName, String receiverPhone,
            String zipCode, String address, String addressDetail, String idempotencyKey,
            String requestHash
    ) {
        this.memberId = memberId;
        this.sellerId = sellerId;
        this.orderId = orderId;
        this.purchaseType = purchaseType;
        this.receiverName = receiverName;
        this.receiverPhone = receiverPhone;
        this.zipCode = zipCode;
        this.address = address;
        this.addressDetail = addressDetail;
        this.status = DeliveryStatus.REQUESTED;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
    }

    // 택배사와 송장번호를 등록하고 배송요청을 배송준비로 바꾼다.
    // 이미 같은 송장 정보로 준비 처리된 경우에는 아무것도 하지 않아 재요청해도 안전하다.
    public void prepare(String carrier, String trackingNumber) {
        if (carrier == null || carrier.isBlank()) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_CARRIER_REQUIRED);
        }
        if (trackingNumber == null || trackingNumber.isBlank()) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_TRACKING_NUMBER_REQUIRED);
        }
        String newCarrier = carrier.trim();
        String newTrackingNumber = trackingNumber.trim();

        if (this.status == DeliveryStatus.PREPARING) {
            if (newCarrier.equals(this.carrier) && newTrackingNumber.equals(this.trackingNumber)) {
                return;
            }
            throw new ServiceException(DeliveryErrorCode.DELIVERY_ALREADY_PREPARED);
        }
        if (this.status != DeliveryStatus.REQUESTED) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_INVALID_STATUS);
        }

        this.carrier = newCarrier;
        this.trackingNumber = newTrackingNumber;
        this.status = DeliveryStatus.PREPARING;
    }

    // 배송준비를 배송중으로 바꾸고 출발 시각을 기록한다. 이미 배송중이면 아무것도 하지 않아 재요청해도 안전하다.
    public void ship() {
        if (this.status == DeliveryStatus.SHIPPING) {
            return;
        }
        if (this.status != DeliveryStatus.PREPARING) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_INVALID_STATUS);
        }

        this.status = DeliveryStatus.SHIPPING;
        this.shippedAt = LocalDateTime.now();
    }

    // 배송중을 배송완료로 바꾸고 완료 시각을 기록한다. 이미 배송완료면 아무것도 하지 않아 재실행해도 안전하다.
    public void deliver() {
        if (this.status == DeliveryStatus.DELIVERED) {
            return;
        }
        if (this.status != DeliveryStatus.SHIPPING) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_INVALID_STATUS);
        }

        this.status = DeliveryStatus.DELIVERED;
        this.deliveredAt = LocalDateTime.now();
    }
}
