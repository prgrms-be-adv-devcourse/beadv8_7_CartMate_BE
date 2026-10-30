package com.cartmate.cartmate.boundedContext.trust.dto;

import com.cartmate.cartmate.boundedContext.trust.domain.SellerTrustScore;
import com.cartmate.cartmate.boundedContext.trust.domain.TrustLevel;

// 판매자 신뢰도 조회 응답. 거래 표본이 부족한 NEW 등급이면 score는 null이다.
public record SellerTrustResponse(
        Long sellerId,
        Integer score,
        TrustLevel level
) {
    public static SellerTrustResponse from(SellerTrustScore trustScore) {
        return new SellerTrustResponse(trustScore.getSellerId(), trustScore.getScore(), trustScore.getLevel());
    }

    // 점수 행이 없는 판매자(거래 이력이 아직 없거나 재계산 전)는 점수 없는 NEW로 응답한다.
    public static SellerTrustResponse unrated(Long sellerId) {
        return new SellerTrustResponse(sellerId, null, TrustLevel.NEW);
    }
}
