package com.cartmate.cartmate.boundedContext.payout.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 판매자 등록 이벤트로 복제한 판매자 정보. 지급 시 판매자의 회원 ID를 찾는 데 쓴다.
@Entity
@Table(name = "payout_seller")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayoutSeller {

    @Id
    @Column(name = "seller_id")
    private Long sellerId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    public static PayoutSeller create(Long sellerId, Long memberId) {
        PayoutSeller payoutSeller = new PayoutSeller();
        payoutSeller.sellerId = sellerId;
        payoutSeller.memberId = memberId;
        return payoutSeller;
    }
}
