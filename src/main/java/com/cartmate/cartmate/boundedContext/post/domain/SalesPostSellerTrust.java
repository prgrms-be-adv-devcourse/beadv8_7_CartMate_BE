package com.cartmate.cartmate.boundedContext.post.domain;

import com.cartmate.cartmate.global.code.SalesPostErrorCode;
import com.cartmate.cartmate.global.exception.ServiceException;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

// trust가 계산한 판매자 신뢰도의 복제본. 판매글 목록·상세에 판매자 점수를 보여줄 때 쓴다.
// 판매자 복제(SalesPostSeller)와 분리해, 판매자 정보가 다시 동기화돼도 점수가 지워지지 않고 이벤트 순서에도 의존하지 않는다.
@Entity
@Table(name = "post_seller_trust")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class SalesPostSellerTrust {

    @Id
    @Column(name = "seller_id")
    private Long sellerId;   // 자동 생성 없이 원본 판매자 ID를 그대로 쓴다

    @Column
    private Integer score;   // NEW 등급이면 null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SellerTrustLevel level;

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;   // trust가 계산한 시각. 늦게 도착한 오래된 이벤트를 걸러내는 기준

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 판매자의 신뢰도 복제본을 처음 만든다.
    // 판매자 ID·등급·계산 시각이 없으면 SELLER_TRUST_INVALID 예외가 발생한다.
    public static SalesPostSellerTrust create(Long sellerId, Integer score, SellerTrustLevel level,
                                              LocalDateTime calculatedAt) {
        if (sellerId == null || level == null || calculatedAt == null) {
            throw new ServiceException(SalesPostErrorCode.SELLER_TRUST_INVALID);
        }

        SalesPostSellerTrust trust = new SalesPostSellerTrust();
        trust.sellerId = sellerId;
        trust.score = score;
        trust.level = level;
        trust.calculatedAt = calculatedAt;
        return trust;
    }

    // 저장된 값보다 오래된 계산 결과이면 무시하고 false를 반환한다. 이벤트가 순서대로 오지 않아도 최신 값이 남는다.
    // 값을 바꿨으면 true를 반환한다. 등급이나 계산 시각이 없으면 SELLER_TRUST_INVALID 예외가 발생한다.
    public boolean update(Integer score, SellerTrustLevel level, LocalDateTime calculatedAt) {
        if (level == null || calculatedAt == null) {
            throw new ServiceException(SalesPostErrorCode.SELLER_TRUST_INVALID);
        }

        if (calculatedAt.isBefore(this.calculatedAt)) {
            return false;
        }

        this.score = score;
        this.level = level;
        this.calculatedAt = calculatedAt;
        return true;
    }
}
