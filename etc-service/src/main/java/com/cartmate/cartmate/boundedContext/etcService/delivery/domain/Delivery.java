package com.cartmate.cartmate.boundedContext.etcService.delivery.domain;

import com.cartmate.cartmate.shared.delivery.DeliveryCancelReason;
import com.cartmate.cartmate.shared.delivery.PurchaseType;
import com.cartmate.cartmate.boundedContext.etcService.delivery.exception.DeliveryErrorCode;
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

    // 배송 생성 시점의 주문번호 스냅샷. 주문번호가 없는 주문(공유 공동구매)과 이전에 만든 배송은 null이다.
    @Column(name = "order_number")
    private String orderNumber;

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

    @Column(name = "prepared_at")
    private LocalDateTime preparedAt;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;    //배송출발시간

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;  //배송 완료시간

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;  //구매확정 시간

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    // 왜 취소됐는지. 이 칸이 생기기 전에 취소된 배송은 null이다.
    // 칸 종류를 문자열로 정해 둔다. 정하지 않으면 MySQL enum 칸이 만들어져 사유를 추가할 때마다 칸을 바꿔야 한다.
    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_reason", columnDefinition = "varchar(30)")
    private DeliveryCancelReason cancelReason;

    // 분산락이 뚫리거나 행 락을 쓰는 이벤트 경로(취소 판정)와 겹칠 때 늦게 커밋한 쪽을 실패시킨다.
    // 이미 있는 행은 컬럼이 추가될 때 0으로 채워진다(기본값). null이면 버전 비교가 맞지 않아 수정이 모두 실패한다.
    @Version
    @Column(name = "version", nullable = false, columnDefinition = "bigint not null default 0")
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;    //배송생성시간

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;


    public static Delivery create(
            Long memberId, Long sellerId, Long orderId, String orderNumber, PurchaseType purchaseType,
            String receiverName, String receiverPhone,
            String zipCode, String address, String addressDetail, String idempotencyKey,
            String requestHash
    ) {
        return new Delivery(
                memberId, sellerId, orderId, orderNumber, purchaseType,
                receiverName, receiverPhone, zipCode, address, addressDetail, idempotencyKey, requestHash
        );
    }

    private Delivery(
            Long memberId, Long sellerId, Long orderId, String orderNumber, PurchaseType purchaseType,
            String receiverName, String receiverPhone,
            String zipCode, String address, String addressDetail, String idempotencyKey,
            String requestHash
    ) {
        this.memberId = memberId;
        this.sellerId = sellerId;
        this.orderId = orderId;
        this.orderNumber = orderNumber;
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

    public BuyerCancelVerdict buyerCancelVerdict(LocalDateTime requestedAt) {
        if (this.status == DeliveryStatus.CANCELED || this.status == DeliveryStatus.REQUESTED) {
            return BuyerCancelVerdict.ALLOWED;
        }
        if (requestedAt == null || this.preparedAt == null || !this.preparedAt.isAfter(requestedAt)) {
            return BuyerCancelVerdict.NOT_CANCELABLE;
        }
        if (this.status == DeliveryStatus.PREPARING) {
            return BuyerCancelVerdict.ALLOWED;
        }
        return BuyerCancelVerdict.SHIPPED_AFTER_REQUEST;
    }

    public void cancelByBuyerRequest(LocalDateTime requestedAt, LocalDateTime canceledAt) {
        if (this.status == DeliveryStatus.CANCELED) {
            return;
        }
        if (buyerCancelVerdict(requestedAt) != BuyerCancelVerdict.ALLOWED) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_NOT_CANCELABLE);
        }

        this.status = DeliveryStatus.CANCELED;
        this.canceledAt = canceledAt;
        this.cancelReason = DeliveryCancelReason.BUYER_CANCELED;
    }

    // 판매글 내림으로 인한 취소: 판매자가 판매를 멈춘 것이라 요청 시각과 상관없이 배송준비중(판매자 확인 후)까지 취소한다.
    // 발송(배송중) 이후면 취소할 수 없다. 이미 취소된 배송은 취소된 것으로 본다.
    public boolean isCancelableByPostHidden() {
        return this.status == DeliveryStatus.CANCELED
                || this.status == DeliveryStatus.REQUESTED
                || this.status == DeliveryStatus.PREPARING;
    }

    // 취소 시각은 밖에서 받는다. 한 번의 판매글 내림으로 여러 배송이 취소될 때 같은 시각으로 남기기 위해서다.
    public void cancelByPostHidden(LocalDateTime canceledAt) {
        if (this.status == DeliveryStatus.CANCELED) {
            return;
        }
        if (!isCancelableByPostHidden()) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_NOT_CANCELABLE);
        }

        this.status = DeliveryStatus.CANCELED;
        this.canceledAt = canceledAt;
        this.cancelReason = DeliveryCancelReason.SALES_STOPPED;
    }

    // 판매자가 품절 같은 사정으로 자기 배송을 취소한다. 물건이 나가기 전(배송요청·배송준비중)까지만 된다.
    // 송장 정보는 지우지 않는다. 송장을 발급한 뒤 취소된 배송인지 나중에 확인할 수 있게 남긴다.
    // 같은 사유로 다시 누르면(더블클릭 등) 아무것도 바꾸지 않는다. 실제로 취소했으면 true를 돌려준다.
    // 사유는 한 번 정하면 바꾸지 않는다. 이미 주문 서버와 구매자 화면에 퍼졌기 때문이다.
    public boolean cancelBySeller(DeliveryCancelReason reason, LocalDateTime canceledAt) {
        if (reason == null) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_CANCEL_REASON_REQUIRED);
        }
        if (!reason.isSellerReason()) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_CANCEL_REASON_NOT_ALLOWED);
        }
        if (this.status == DeliveryStatus.CANCELED) {
            if (reason == this.cancelReason) {
                return false;
            }
            throw alreadyCanceled();
        }
        if (this.status != DeliveryStatus.REQUESTED && this.status != DeliveryStatus.PREPARING) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_NOT_CANCELABLE_BY_SELLER);
        }

        this.status = DeliveryStatus.CANCELED;
        this.canceledAt = canceledAt;
        this.cancelReason = reason;
        return true;
    }

    // 판매자가 이미 취소된 배송을 누르면 왜 취소됐는지 알려 준다. 사유가 없는 예전 취소는 문구만 보낸다.
    private ServiceException alreadyCanceled() {
        DeliveryErrorCode code = DeliveryErrorCode.DELIVERY_ALREADY_CANCELED;
        if (this.cancelReason == null) {
            return new ServiceException(code);
        }
        return new ServiceException(code.resultCode(),
                code.getMsg() + " (사유: " + this.cancelReason.getLabel() + ")");
    }

    // 택배사와 송장번호를 등록하고 배송요청을 배송준비로 바꾼다.
    // 이미 같은 송장 정보로 준비 처리된 경우에는 아무것도 하지 않아 재요청해도 안전하다.
    public void prepare(String carrier, String trackingNumber) {
        if (this.status == DeliveryStatus.CANCELED) {
            throw alreadyCanceled();
        }
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
        this.preparedAt = LocalDateTime.now();
    }

    // 배송준비를 배송중으로 바꾸고 출발 시각을 기록한다. 이미 배송중이면 아무것도 하지 않아 재요청해도 안전하다.
    public void ship() {
        if (this.status == DeliveryStatus.CANCELED) {
            throw alreadyCanceled();
        }
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

    // 배송완료를 구매확정으로 바꾸고 확정 시각을 기록한다. 이미 구매확정이면 아무것도 하지 않아 재요청해도 안전하다.
    // 상태를 실제로 바꿨으면 true, 이미 구매확정이라 바꾸지 않았으면 false를 반환한다.
    public boolean confirm() {
        if (this.status == DeliveryStatus.CONFIRMED) {
            return false;
        }
        if (this.status != DeliveryStatus.DELIVERED) {
            throw new ServiceException(DeliveryErrorCode.DELIVERY_INVALID_STATUS);
        }

        this.status = DeliveryStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
        return true;
    }
}
