package com.cartmate.cartmate.boundedContext.trust.domain;

import com.cartmate.cartmate.boundedContext.trust.exception.TrustErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

// 판매자 신뢰도에 반영할 거래 이력 한 건. 받은 이벤트 하나가 한 행이 되며, 점수 계산 때 판매자·유형별로 센다.
// (type, source_key)가 같은 이벤트가 다시 오면 unique 제약으로 막아 중복을 한 번만 반영한다.
@Entity
@Table(
        name = "seller_trust_history",
        uniqueConstraints = @UniqueConstraint(name = "uk_seller_trust_history_type_source",
                columnNames = {"type", "source_key"}),
        indexes = @Index(name = "idx_seller_trust_history_seller_type_occurred",
                columnList = "seller_id, type, occurred_at")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class SellerTrustHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TrustHistoryType type;

    @Column(name = "source_key", nullable = false)
    private String sourceKey;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 받은 이벤트를 거래 이력으로 만든다. sourceKey는 같은 이벤트가 다시 와도 같은 값이 나오도록 호출하는 쪽에서 정한다.
    // 판매자 ID가 없으면 TRUST_SELLER_ID_REQUIRED, 유형이 없으면 TRUST_HISTORY_TYPE_REQUIRED,
    // sourceKey가 없거나 비어 있으면 TRUST_SOURCE_KEY_REQUIRED, 발생 시각이 없으면 TRUST_OCCURRED_AT_REQUIRED 예외가 발생한다.
    public static SellerTrustHistory create(Long sellerId, TrustHistoryType type, String sourceKey,
                                            LocalDateTime occurredAt) {
        if (sellerId == null) {
            throw new ServiceException(TrustErrorCode.TRUST_SELLER_ID_REQUIRED);
        }

        if (type == null) {
            throw new ServiceException(TrustErrorCode.TRUST_HISTORY_TYPE_REQUIRED);
        }

        if (sourceKey == null || sourceKey.isBlank()) {
            throw new ServiceException(TrustErrorCode.TRUST_SOURCE_KEY_REQUIRED);
        }

        if (occurredAt == null) {
            throw new ServiceException(TrustErrorCode.TRUST_OCCURRED_AT_REQUIRED);
        }

        return new SellerTrustHistory(sellerId, type, sourceKey, occurredAt);
    }

    private SellerTrustHistory(Long sellerId, TrustHistoryType type, String sourceKey, LocalDateTime occurredAt) {
        this.sellerId = sellerId;
        this.type = type;
        this.sourceKey = sourceKey;
        this.occurredAt = occurredAt;
    }
}
