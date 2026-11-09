package com.cartmate.cartmate.boundedContext.etcService.delivery.domain;

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

import java.time.LocalDateTime;

/**
 * 판매글 내림으로 한 판매자 몫이 취소됐다는 메모. 배송이 아직 없을 때 취소 요청이 먼저 오면,
 * 나중에 온 배송 생성이 이 메모를 보고 그 판매자 배송을 만들지 않는다 (ADR 0008).
 * 배송 생성과 취소는 주문 단위 상태 줄({@link DeliveryOrderState})을 잠가 순서를 맞춘다.
 */
@Entity
@Table(name = "delivery_seller_cancel",
        uniqueConstraints = @UniqueConstraint(name = "uk_delivery_seller_cancel",
                columnNames = {"order_id", "purchase_type", "seller_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliverySellerCancel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purchase_type", nullable = false, columnDefinition = "varchar(20)")
    private PurchaseType purchaseType;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
