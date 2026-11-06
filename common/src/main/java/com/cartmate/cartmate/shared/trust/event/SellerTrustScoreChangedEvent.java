package com.cartmate.cartmate.shared.trust.event;

import java.time.LocalDateTime;

// 판매자의 신뢰도 점수나 등급이 바뀔 때 발행. post가 받아서 판매글에 보여줄 판매자 정보를 복제한다.
public record SellerTrustScoreChangedEvent(
        Long sellerId,
        Integer score,             // 거래 표본이 부족한 NEW 등급이면 null
        String level,              // TrustLevel 이름 (NEW, LOW, NORMAL, HIGH)
        LocalDateTime calculatedAt
) {
}
