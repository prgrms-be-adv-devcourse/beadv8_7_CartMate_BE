package com.cartmate.cartmate.boundedContext.trust.domain;

import com.cartmate.cartmate.boundedContext.trust.exception.TrustErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "seller_trust_score")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class SellerTrustScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false, unique = true)
    private Long sellerId;

    @Column
    private Integer score;   // NEW 등급이면 null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TrustLevel level;

    @Column(name = "confirmed_count", nullable = false)
    private int confirmedCount;

    @Column(name = "fault_claim_count", nullable = false)
    private int faultClaimCount;

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;   // 집계 건수나 점수·등급이 마지막으로 바뀐 시각

    @Version
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 판매자의 신뢰도 점수를 처음 만든다.
    // 판매자 ID가 없으면 TRUST_SELLER_ID_REQUIRED, 집계값·평가 결과·계산 시각이 없으면 TRUST_INVALID_METRICS 예외가 발생한다.
    public static SellerTrustScore create(Long sellerId, TrustMetrics metrics, TrustEvaluation evaluation,
                                          LocalDateTime calculatedAt) {
        if (sellerId == null) {
            throw new ServiceException(TrustErrorCode.TRUST_SELLER_ID_REQUIRED);
        }

        SellerTrustScore trustScore = new SellerTrustScore(sellerId);
        trustScore.recalculate(metrics, evaluation, calculatedAt);
        return trustScore;
    }

    // 새로 계산한 집계값과 평가 결과로 점수를 덮어쓴다.
    // 집계값·평가 결과·계산 시각이 없으면 TRUST_INVALID_METRICS 예외가 발생한다.
    public void recalculate(TrustMetrics metrics, TrustEvaluation evaluation, LocalDateTime calculatedAt) {
        if (metrics == null || evaluation == null || calculatedAt == null) {
            throw new ServiceException(TrustErrorCode.TRUST_INVALID_METRICS);
        }

        this.score = evaluation.score();
        this.level = evaluation.level();
        this.confirmedCount = metrics.confirmedCount();
        this.faultClaimCount = metrics.faultClaimCount();
        this.calculatedAt = calculatedAt;
    }

    // 평가 결과가 현재 점수·등급과 다르면 true. 변경 이벤트 발행 여부를 가릴 때 쓴다.
    public boolean isChanged(TrustEvaluation evaluation) {
        return !Objects.equals(score, evaluation.score()) || level != evaluation.level();
    }

    // 집계 건수나 점수·등급이 현재 값과 하나라도 다르면 true. 모두 같으면 다시 저장할 필요가 없다.
    public boolean isChanged(TrustMetrics metrics, TrustEvaluation evaluation) {
        return confirmedCount != metrics.confirmedCount()
                || faultClaimCount != metrics.faultClaimCount()
                || isChanged(evaluation);
    }

    private SellerTrustScore(Long sellerId) {
        this.sellerId = sellerId;
    }
}
